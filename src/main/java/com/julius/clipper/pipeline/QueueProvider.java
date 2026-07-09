package com.julius.clipper.pipeline;

import com.julius.clipper.domain.Task;

public interface QueueProvider {
    void push(Task task);
    Task pop(TaskType taskType);
    void complete(Task task);
    void fail(String taskId, String error);
    void touchTaskHeartbeat(String taskId);
    long getQueueDepth(TaskType taskType);
}
