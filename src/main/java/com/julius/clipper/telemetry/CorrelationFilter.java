package com.julius.clipper.telemetry;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationFilter implements Filter {

    public static final String CORRELATION_ID_MDC_KEY = "correlation_id";
    public static final String REQUEST_ID_MDC_KEY = "request_id";

    private final ApiMetrics apiMetrics;

    public CorrelationFilter(ApiMetrics apiMetrics) {
        this.apiMetrics = apiMetrics;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        io.micrometer.core.instrument.Timer.Sample sample = null;
        String method = "UNKNOWN";
        String uri = "UNKNOWN";
        int status = 200;

        if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse) {
            if (apiMetrics != null) {
                sample = apiMetrics.startRequest();
            }
            method = httpRequest.getMethod();
            uri = httpRequest.getRequestURI();

            String correlationId = httpRequest.getHeader("X-Correlation-ID");
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = httpRequest.getHeader("X-Request-ID");
            }
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = "corr-" + UUID.randomUUID().toString().substring(0, 8);
            }

            String requestId = httpRequest.getHeader("X-Request-ID");
            if (requestId == null || requestId.isBlank()) {
                requestId = "req-" + UUID.randomUUID().toString().substring(0, 8);
            }

            MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
            MDC.put(REQUEST_ID_MDC_KEY, requestId);

            httpResponse.setHeader("X-Correlation-ID", correlationId);
            httpResponse.setHeader("X-Request-ID", requestId);
        }

        try {
            chain.doFilter(request, response);
            if (response instanceof HttpServletResponse httpResponse) {
                status = httpResponse.getStatus();
            }
        } catch (ServletException | IOException e) {
            status = 500;
            throw e;
        } finally {
            if (sample != null) {
                apiMetrics.recordRequest(sample, method, uri, status);
            }
            MDC.clear();
        }
    }
}
