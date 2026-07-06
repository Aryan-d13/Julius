package com.julius.clipper.actuator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class GpuHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(GpuHealthIndicator.class);

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "nvidia-smi",
                    "--query-gpu=temperature.gpu,utilization.gpu,utilization.memory,memory.total,memory.used",
                    "--format=csv,noheader,nounits"
            );
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                String result = output.toString().trim();
                if (!result.isEmpty()) {
                    String[] tokens = result.split(",");
                    if (tokens.length >= 5) {
                        details.put("gpu.status", "AVAILABLE");
                        details.put("temperature_celsius", Integer.parseInt(tokens[0].trim()));
                        details.put("utilization_gpu_percent", Integer.parseInt(tokens[1].trim()));
                        details.put("utilization_memory_percent", Integer.parseInt(tokens[2].trim()));
                        details.put("memory_total_mb", Integer.parseInt(tokens[3].trim()));
                        details.put("memory_used_mb", Integer.parseInt(tokens[4].trim()));
                        
                        return Health.up().withDetails(details).build();
                    }
                }
            }
            
            // Non-zero exit code or empty output
            details.put("gpu.status", "DRIVER_ERROR");
            details.put("message", "nvidia-smi returned non-zero exit code or empty output.");
            return Health.up().withDetails(details).build();

        } catch (Exception e) {
            log.debug("Nvidia GPU status query failed (normal if executing in non-GPU environment): {}", e.getMessage());
            details.put("gpu.status", "NO_GPU_OR_DRIVER_NOT_FOUND");
            details.put("message", "nvidia-smi command is not available in the current runtime environment.");
            return Health.up().withDetails(details).build();
        }
    }
}
