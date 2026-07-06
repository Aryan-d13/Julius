package com.julius.clipper.pipeline;

import jakarta.annotation.PostConstruct;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import com.julius.clipper.domain.Task;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class WorkerRunner {

    private static final Logger log = LoggerFactory.getLogger(WorkerRunner.class);

    private final QueueProvider queueProvider;
    private final Orchestrator orchestrator;
    private final EventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    private final Map<TaskType, Worker> workers = new HashMap<>();

    // Concurrency limits modeled using Semaphores
    private final Semaphore ioSemaphore = new Semaphore(8);  // DOWNLOAD, DOWNLOAD_VIDEO, INGEST, ANALYZE
    private final Semaphore cpuSemaphore = new Semaphore(2); // CUT, LAYOUT
    private final Semaphore gpuSemaphore = new Semaphore(1); // TRANSCRIBE, SMART_RENDER

    // Java 21 Virtual Thread Executor
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
    
    private final AtomicInteger activeWorkersCount = new AtomicInteger(0);
    private volatile boolean running = false;

    @Autowired
    private ApplicationContext applicationContext;

    public WorkerRunner(QueueProvider queueProvider, 
                        Orchestrator orchestrator, 
                        EventPublisher eventPublisher,
                        MeterRegistry meterRegistry) {
        this.queueProvider = queueProvider;
        this.orchestrator = orchestrator;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;

        // Register custom gauge for active virtual thread workers
        Gauge.builder("clipper.pipeline.active.workers", activeWorkersCount, AtomicInteger::get)
                .description("Total number of virtual thread tasks currently executing in the engine")
                .register(meterRegistry);
    }

    @PostConstruct
    public void init() {
        try {
            workers.put(TaskType.DOWNLOAD, applicationContext.getBean("DOWNLOADWorker", Worker.class));
            workers.put(TaskType.DOWNLOAD_VIDEO, applicationContext.getBean("DOWNLOADWorker", Worker.class));
            workers.put(TaskType.INGEST, applicationContext.getBean("INGESTWorker", Worker.class));
            workers.put(TaskType.TRANSCRIBE, applicationContext.getBean("TRANSCRIBEWorker", Worker.class));
            workers.put(TaskType.ANALYZE, applicationContext.getBean("ANALYZEWorker", Worker.class));
            workers.put(TaskType.SMART_RENDER, applicationContext.getBean("SMART_RENDERWorker", Worker.class));
        } catch (Exception e) {
            log.warn("Failed to find some worker beans, falling back to mock configurations", e);
        }

        // Mock fallback for missing / legacy workers (like CUT / LAYOUT)
        Worker mockFallback = task -> Map.of("status", "mock_completed");
        workers.putIfAbsent(TaskType.DOWNLOAD, mockFallback);
        workers.putIfAbsent(TaskType.DOWNLOAD_VIDEO, mockFallback);
        workers.putIfAbsent(TaskType.INGEST, mockFallback);
        workers.putIfAbsent(TaskType.TRANSCRIBE, mockFallback);
        workers.putIfAbsent(TaskType.ANALYZE, mockFallback);
        workers.putIfAbsent(TaskType.SMART_RENDER, mockFallback);
        workers.putIfAbsent(TaskType.CUT, mockFallback);
        workers.putIfAbsent(TaskType.LAYOUT, mockFallback);
    }

    @org.springframework.context.event.EventListener(org.springframework.context.event.ContextRefreshedEvent.class)
    public void onApplicationEvent() {
        start();
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        executorService.submit(this::pollingLoop);
        log.info("WorkerRunner started successfully.");
    }

    public synchronized void stop() {
        running = false;
        executorService.shutdown();
        log.info("WorkerRunner stopped.");
    }

    private void pollingLoop() {
        List<TaskType> taskTypes = new ArrayList<>(Arrays.asList(TaskType.values()));

        while (running) {
            boolean foundAny = false;
            Collections.shuffle(taskTypes);

            for (TaskType type : taskTypes) {
                Semaphore sem = getSemaphore(type);
                
                // Check if queue has permit (non-blocking acquire)
                if (!sem.tryAcquire()) {
                    continue; // Queue limit reached, try another step type
                }

                try {
                    Task task = queueProvider.pop(type);
                    if (task != null) {
                        foundAny = true;
                        // Submit task execution on virtual threads
                        executorService.submit(() -> executeTaskWithRelease(task, sem));
                    } else {
                        sem.release();
                    }
                } catch (Exception e) {
                    sem.release();
                    log.error("Error polling queue for task type: {}", type, e);
                }
            }

            if (!foundAny) {
                try {
                    Thread.sleep(1000); // Wait 1 second before next poll if queue is empty
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void executeTaskWithRelease(Task task, Semaphore sem) {
        activeWorkersCount.incrementAndGet();
        MDC.put("virtual_thread", String.valueOf(Thread.currentThread().isVirtual()));
        try {
            executeTask(task);
        } finally {
            activeWorkersCount.decrementAndGet();
            MDC.remove("virtual_thread");
            sem.release();
        }
    }

    private void executeTask(Task task) {
        String jobId = task.getJobId();
        String stepName = task.getType().name().toLowerCase();

        // 1. Mark job and step start state in DB and notify API
        if (jobId != null) {
            orchestrator.setStartedAtAtomic(jobId);
            orchestrator.updateCurrentStep(jobId, stepName);
            orchestrator.recordStepStart(jobId, stepName);
            
            eventPublisher.publish(jobId, task.getUserId(), "step_started", 
                    task.getPayload(), stepName, 0, "Step " + stepName + " started", task.getId());
        }

        // 2. Start heartbeat thread for task
        ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                queueProvider.touchTaskHeartbeat(task.getId());
            } catch (Exception e) {
                log.warn("Failed to touch heartbeat for task: {}", task.getId(), e);
            }
        }, 30, 30, TimeUnit.SECONDS);

        String statusOutcome = "SUCCESS";
        long startTime = System.currentTimeMillis();

        try {
            // 3. Process task via worker
            Worker worker = workers.get(task.getType());
            if (worker == null) {
                throw new IllegalStateException("No worker registered for task type: " + task.getType());
            }

            Map<String, Object> result = worker.process(task);

            // 4. Save step completion and publish
            if (jobId != null) {
                orchestrator.recordStepCompletion(jobId, stepName, "completed", null);
                eventPublisher.publish(jobId, task.getUserId(), "step_completed", 
                        result, stepName, 100, "Step " + stepName + " completed", task.getId());
            }

            // 5. Fetch downstream tasks and push
            List<Task> nextTasks = orchestrator.getNextTasks(task, result);
            for (Task nextTask : nextTasks) {
                queueProvider.push(nextTask);
            }

            // 6. Complete task (ACK)
            queueProvider.complete(task);

        } catch (Exception e) {
            statusOutcome = "FAILED";
            log.error("Task execution failed. TaskId: {}, Type: {}", task.getId(), task.getType(), e);
            
            queueProvider.fail(task.getId(), e.getMessage());
            
            if (jobId != null) {
                orchestrator.recordStepCompletion(jobId, stepName, "failed", e.getMessage());
                orchestrator.markJobFailed(jobId, e.getMessage(), task.getUserId());
            }
        } finally {
            heartbeatScheduler.shutdownNow();

            long duration = System.currentTimeMillis() - startTime;

            // Timer: clipper.task.execution.duration
            Timer.builder("clipper.task.execution.duration")
                    .description("Execution timing distributions of pipeline worker loops")
                    .tag("type", task.getType().name())
                    .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                    .publishPercentileHistogram()
                    .register(meterRegistry)
                    .record(duration, TimeUnit.MILLISECONDS);

            // Counter: clipper.tasks.processed.total
            Counter.builder("clipper.tasks.processed.total")
                    .description("Aggregated volume of pipeline steps processed")
                    .tag("type", task.getType().name())
                    .tag("status", statusOutcome)
                    .register(meterRegistry)
                    .increment();
        }
    }

    private Semaphore getSemaphore(TaskType type) {
        return switch (type) {
            case DOWNLOAD, DOWNLOAD_VIDEO, INGEST, ANALYZE -> ioSemaphore;
            case CUT, LAYOUT -> cpuSemaphore;
            case TRANSCRIBE, SMART_RENDER -> gpuSemaphore;
        };
    }
}
