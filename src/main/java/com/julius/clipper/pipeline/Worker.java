package com.julius.clipper.pipeline;

import com.julius.clipper.domain.Task;
import java.util.Map;

public interface Worker {
    Map<String, Object> process(Task task) throws Exception;
}
