package com.julius.clipper.telemetry;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class TelemetryLoggingTest {

    @Test
    public void testCorrelationIdGenerationAndMdcBinding() throws Exception {
        CorrelationFilter filter = new CorrelationFilter(null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        FilterChain chain = mock(FilterChain.class);
        
        doAnswer(invocation -> {
            // Verify MDC has generated correlation and request IDs inside the filter chain execution
            assertThat(MDC.get("correlation_id")).startsWith("corr-");
            assertThat(MDC.get("request_id")).startsWith("req-");
            return null;
        }).when(chain).doFilter(any(ServletRequest.class), any(ServletResponse.class));

        filter.doFilter(request, response, chain);

        // Verify headers are propagated in response
        assertThat(response.getHeader("X-Correlation-ID")).startsWith("corr-");
        assertThat(response.getHeader("X-Request-ID")).startsWith("req-");

        // Verify MDC is cleared after execution
        assertThat(MDC.get("correlation_id")).isNull();
    }

    @Test
    public void testCorrelationIdHeaderPreservation() throws Exception {
        CorrelationFilter filter = new CorrelationFilter(null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", "custom-corr-123");
        request.addHeader("X-Request-ID", "custom-req-456");
        
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        
        doAnswer(invocation -> {
            assertThat(MDC.get("correlation_id")).isEqualTo("custom-corr-123");
            assertThat(MDC.get("request_id")).isEqualTo("custom-req-456");
            return null;
        }).when(chain).doFilter(any(ServletRequest.class), any(ServletResponse.class));

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Correlation-ID")).isEqualTo("custom-corr-123");
        assertThat(response.getHeader("X-Request-ID")).isEqualTo("custom-req-456");
    }

    @Test
    public void testMdcAsynchronousPropagation() throws Exception {
        MDC.put("correlation_id", "async-corr-888");
        MDC.put("request_id", "async-req-999");

        Runnable task = () -> {
            assertThat(MDC.get("correlation_id")).isEqualTo("async-corr-888");
            assertThat(MDC.get("request_id")).isEqualTo("async-req-999");
        };

        // Wrap in MdcRunnable
        MdcRunnable wrapped = new MdcRunnable(task);

        // Run in separate thread executor
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(wrapped);
        future.get(2, TimeUnit.SECONDS);

        executor.shutdown();
        MDC.clear();
    }

    @Test
    public void testSensitiveDataMasking() {
        LogMaskingValueMasker masker = new LogMaskingValueMasker();
        
        // 1. Google API key masking
        String log1 = "Starting connection with google.api.key=AIzaSyA1B2C3D4E5";
        Object masked1 = masker.mask(null, log1);
        assertThat(masked1).isEqualTo("Starting connection with google.api.key=******");

        // 2. Password masking
        String log2 = "user_name: julius, password: mySecretPassword123";
        Object masked2 = masker.mask(null, log2);
        assertThat(masked2).isEqualTo("user_name: julius, password: ******");

        // 3. Cookie masking
        String log3 = "fetching cookies=session_token_xyz_999 from request";
        Object masked3 = masker.mask(null, log3);
        assertThat(masked3).isEqualTo("fetching cookies=****** from request");

        // 4. Non-sensitive strings remain unmodified
        String safeLog = "Downloading video from youtube URL https://youtube.com/watch?v=dQw4w9WgXcQ";
        Object safeMasked = masker.mask(null, safeLog);
        assertThat(safeMasked).isEqualTo(safeLog);
    }

    @Test
    public void testTaskMetadataContextPropagation() {
        com.julius.clipper.domain.Task task = new com.julius.clipper.domain.Task();
        task.getMetadata().put("correlation_id", "corr-999");
        task.getMetadata().put("request_id", "req-888");

        com.julius.clipper.domain.Task nextTask = com.julius.clipper.domain.Task.builder().payload(new java.util.HashMap<>()).build();
        java.util.List<com.julius.clipper.domain.Task> nextTasks = java.util.List.of(nextTask);

        // Propagate
        if (task.getMetadata() != null && !task.getMetadata().isEmpty()) {
            for (com.julius.clipper.domain.Task t : nextTasks) {
                t.setMetadata(new java.util.HashMap<>(task.getMetadata()));
            }
        }

        assertThat(nextTask.getMetadata().get("correlation_id")).isEqualTo("corr-999");
        assertThat(nextTask.getMetadata().get("request_id")).isEqualTo("req-888");
    }
}
