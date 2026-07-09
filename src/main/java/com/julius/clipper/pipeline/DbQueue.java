package com.julius.clipper.pipeline;

import com.julius.clipper.domain.Task;
import com.julius.clipper.repository.TaskRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DbQueue implements QueueProvider {

    private final TaskRepository taskRepository;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    public DbQueue(TaskRepository taskRepository, io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        this.taskRepository = taskRepository;
        this.meterRegistry = meterRegistry;
    }

    @Override
    @Transactional
    public void push(Task task) {
        io.micrometer.core.instrument.Counter.builder("clipper.queue.operations")
                .tag("operation", "push")
                .tag("task_type", task.getType().name())
                .register(meterRegistry)
                .increment();

        task.setStatus(TaskStatus.PENDING);
        taskRepository.save(task);
    }

    @Override
    @Transactional
    public Task pop(TaskType taskType) {
        List<Task> tasks = taskRepository.findFirstForUpdateSkipLocked(
                taskType, 
                TaskStatus.PENDING, 
                PageRequest.of(0, 1)
        );
        if (tasks.isEmpty()) {
            return null;
        }
        Task task = tasks.get(0);
        task.setStatus(TaskStatus.PROCESSING);
        task.setStartedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());

        io.micrometer.core.instrument.Counter.builder("clipper.queue.operations")
                .tag("operation", "pop")
                .tag("task_type", taskType.name())
                .register(meterRegistry)
                .increment();

        return taskRepository.save(task);
    }

    @Override
    @Transactional
    public void complete(Task task) {
        taskRepository.deleteById(task.getId());

        io.micrometer.core.instrument.Counter.builder("clipper.queue.operations")
                .tag("operation", "complete")
                .tag("task_type", task.getType().name())
                .register(meterRegistry)
                .increment();
    }

    @Override
    @Transactional
    public void fail(String taskId, String error) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(TaskStatus.FAILED);
            task.setError(error);
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.save(task);

            io.micrometer.core.instrument.Counter.builder("clipper.queue.operations")
                    .tag("operation", "fail")
                    .tag("task_type", task.getType().name())
                    .register(meterRegistry)
                    .increment();
        });
    }

    @Override
    @Transactional
    public void touchTaskHeartbeat(String taskId) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.save(task);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public long getQueueDepth(TaskType taskType) {
        return taskRepository.countByTypeAndStatus(taskType, TaskStatus.PENDING);
    }
}
