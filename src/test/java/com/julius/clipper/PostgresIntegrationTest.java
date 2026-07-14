package com.julius.clipper;

import com.julius.clipper.domain.*;
import com.julius.clipper.domain.dto.JobConfig;
import com.julius.clipper.repository.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test running against a real PostgreSQL 16 container.
 * Validates:
 * - Flyway migration executes successfully on PostgreSQL
 * - Hibernate schema validation passes (ddl-auto=validate)
 * - Full Spring Boot application context loads
 * - CRUD operations work on all critical entities
 * - Health actuator endpoint returns UP
 */
@org.springframework.boot.test.context.SpringBootTest(
    webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "clipper.queue.type=db",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
    }
)
@Tag("integration")
public class PostgresIntegrationTest {

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
        org.junit.jupiter.api.Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available, skipping Postgres test");
    }

    // Mock Redis dependencies — not required for database migration testing
    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobClipRepository jobClipRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    // ─── Context & Migration ────────────────────────────────────────────────

    @Test
    void contextLoads() {
        // If Flyway migration fails or Hibernate validate rejects the schema,
        // the application context will not load and this test will fail.
    }

    // ─── Health Endpoint ────────────────────────────────────────────────────

    @Test
    void healthEndpointReturnsUp() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/actuator/health", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().get("status"));
    }

    // ─── User CRUD ──────────────────────────────────────────────────────────

    @Test
    void userCrudOperations() {
        User user = User.builder()
                .email("test-" + UUID.randomUUID() + "@julius.local")
                .fullName("Test User")
                .build();

        User saved = userRepository.save(user);
        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());

        User found = userRepository.findById(saved.getId()).orElse(null);
        assertNotNull(found);
        assertEquals(saved.getEmail(), found.getEmail());
        assertEquals("Test User", found.getFullName());
    }

    // ─── Job CRUD ───────────────────────────────────────────────────────────

    @Test
    void jobCrudWithEnumPersistence() {
        JobConfig config = JobConfig.builder()
                .url("https://youtube.com/watch?v=test")
                .count(1)
                .minDuration(10)
                .maxDuration(60)
                .templateRef("test-template")
                .languageMode("auto")
                .copyLanguage("en")
                .build();

        Job job = Job.builder()
                .userId("test-user")
                .correlationId("corr-" + UUID.randomUUID())
                .config(config)
                .clipCount(1)
                .status(JobDBStatus.PENDING)
                .build();

        Job saved = jobRepository.save(job);
        assertNotNull(saved.getId());

        Job found = jobRepository.findById(saved.getId()).orElse(null);
        assertNotNull(found);
        assertEquals(JobDBStatus.PENDING, found.getStatus());
        assertEquals("https://youtube.com/watch?v=test", found.getConfig().getUrl());
        assertEquals(1, found.getClipCount());
    }

    // ─── Task CRUD ──────────────────────────────────────────────────────────

    @Test
    void taskCrudWithEnumPersistence() {
        com.julius.clipper.domain.Task task = com.julius.clipper.domain.Task.builder()
                .type(com.julius.clipper.pipeline.TaskType.DOWNLOAD)
                .status(com.julius.clipper.pipeline.TaskStatus.PENDING)
                .build();

        com.julius.clipper.domain.Task saved = taskRepository.save(task);
        assertNotNull(saved.getId());

        com.julius.clipper.domain.Task found = taskRepository.findById(saved.getId()).orElse(null);
        assertNotNull(found);
        assertEquals(com.julius.clipper.pipeline.TaskType.DOWNLOAD, found.getType());
        assertEquals(com.julius.clipper.pipeline.TaskStatus.PENDING, found.getStatus());
    }

    // ─── JobClip Unique Constraints ─────────────────────────────────────────

    @Test
    void jobClipUniqueConstraintEnforced() {
        String jobId = UUID.randomUUID().toString();

        JobClip clip1 = JobClip.builder()
                .jobId(jobId)
                .clipIndex(0)
                .filename("clip_0.mp4")
                .build();
        jobClipRepository.save(clip1);

        // Attempt duplicate clip_index — should fail
        JobClip duplicate = JobClip.builder()
                .jobId(jobId)
                .clipIndex(0)
                .filename("clip_0_duplicate.mp4")
                .build();

        assertThrows(Exception.class, () -> {
            jobClipRepository.saveAndFlush(duplicate);
        });
    }

    // ─── Seed Data Verification ─────────────────────────────────────────────

    @Test
    void devSeedDataIsPresent() {
        // R__seed_dev_data.sql should have inserted the dev user
        User devUser = userRepository.findById("dev-user-0001").orElse(null);
        assertNotNull(devUser, "Dev seed user should be present after Flyway migration");
        assertEquals("dev@julius.local", devUser.getEmail());
    }
}
