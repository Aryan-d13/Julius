package com.julius.clipper;

import com.julius.clipper.domain.Task;
import com.julius.clipper.pipeline.*;
import com.julius.clipper.repository.TaskRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency test validating PostgreSQL FOR UPDATE SKIP LOCKED behavior.
 * Ensures that multiple concurrent workers never claim the same task,
 * validating the production queue semantics that H2 silently ignores.
 */
@SpringBootTest(properties = {
    "clipper.queue.type=db",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=true"
})
@Tag("integration")
public class QueueConcurrencyTest {

    private static final Logger log = LoggerFactory.getLogger(QueueConcurrencyTest.class);

    static PostgreSQLContainer<?> postgres;

    private static boolean isDockerAvailable() {
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable e) {
            return false;
        }
    }

    @org.springframework.test.context.DynamicPropertySource
    static void configureProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        if (isDockerAvailable()) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine");
            postgres.start();
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        }
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        org.junit.jupiter.api.Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available, skipping Postgres queue concurrency test");
    }

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private DbQueue dbQueue;

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void skipLockedPreventsDoubleClaiming() throws Exception {
        int taskCount = 10;
        int workerCount = 20;

        // ── Setup: Create PENDING tasks ──────────────────────────────────────
        for (int i = 0; i < taskCount; i++) {
            Task task = Task.builder()
                    .id(UUID.randomUUID().toString())
                    .type(TaskType.DOWNLOAD)
                    .status(TaskStatus.PENDING)
                    .build();
            taskRepository.saveAndFlush(task);
        }

        log.info("Created {} PENDING tasks. Launching {} concurrent workers.", taskCount, workerCount);

        // ── Execute: Concurrent workers try to claim tasks ───────────────────
        ConcurrentHashMap<String, String> claimedTasks = new ConcurrentHashMap<>();
        AtomicInteger duplicateClaims = new AtomicInteger(0);
        AtomicInteger nullResults = new AtomicInteger(0);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(workerCount);

        ExecutorService executor = Executors.newFixedThreadPool(workerCount);

        for (int i = 0; i < workerCount; i++) {
            final int workerId = i;
            executor.submit(() -> {
                try {
                    // All workers wait here until the gate opens — maximizes contention
                    startGate.await(10, TimeUnit.SECONDS);

                    Task claimed = dbQueue.pop(TaskType.DOWNLOAD);

                    if (claimed == null) {
                        nullResults.incrementAndGet();
                        log.debug("Worker {} got null (no tasks available)", workerId);
                    } else {
                        String previousOwner = claimedTasks.putIfAbsent(
                                claimed.getId(), 
                                "worker-" + workerId
                        );
                        if (previousOwner != null) {
                            duplicateClaims.incrementAndGet();
                            log.error("DUPLICATE CLAIM: Task {} claimed by both {} and worker-{}", 
                                    claimed.getId(), previousOwner, workerId);
                        } else {
                            log.debug("Worker {} claimed task {}", workerId, claimed.getId());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Worker {} encountered error: {}", workerId, e.getMessage());
                } finally {
                    finishGate.countDown();
                }
            });
        }

        // Release all workers simultaneously
        startGate.countDown();

        // Wait for all workers to finish
        boolean completed = finishGate.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All workers should complete within 30 seconds");

        // ── Assertions ──────────────────────────────────────────────────────
        log.info("Results: {} tasks claimed, {} null results, {} duplicate claims",
                claimedTasks.size(), nullResults.get(), duplicateClaims.get());

        assertEquals(0, duplicateClaims.get(),
                "FOR UPDATE SKIP LOCKED must prevent any task from being claimed by two workers");

        assertEquals(taskCount, claimedTasks.size(),
                "All " + taskCount + " tasks should be claimed exactly once");

        assertEquals(workerCount - taskCount, nullResults.get(),
                "Excess workers (beyond available tasks) should receive null");
    }

    @Test
    void skipLockedMaintainsOrderUnderContention() throws Exception {
        // Create tasks with known ordering (by createdAt)
        List<String> taskIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Task task = Task.builder()
                    .id(UUID.randomUUID().toString())
                    .type(TaskType.ANALYZE)
                    .status(TaskStatus.PENDING)
                    .build();
            Task saved = taskRepository.saveAndFlush(task);
            taskIds.add(saved.getId());
            // Small delay to ensure distinct createdAt timestamps
            Thread.sleep(10);
        }

        // Sequential pop should return tasks in createdAt order
        List<String> claimedOrder = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Task claimed = dbQueue.pop(TaskType.ANALYZE);
            assertNotNull(claimed, "Task " + i + " should be available");
            claimedOrder.add(claimed.getId());
        }

        // Sixth pop should return null — all tasks claimed
        Task extra = dbQueue.pop(TaskType.ANALYZE);
        assertNull(extra, "No more ANALYZE tasks should be available");

        assertEquals(taskIds, claimedOrder,
                "Tasks should be claimed in createdAt ASC order (FIFO)");
    }
}
