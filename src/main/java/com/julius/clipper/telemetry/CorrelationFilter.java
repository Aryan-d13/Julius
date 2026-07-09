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

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse) {
            String correlationId = httpRequest.getHeader("X-Correlation-ID");
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = httpRequest.getHeader("X-Request-ID");
            }
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = httpRequest.getHeader("traceparent");
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
        } finally {
            MDC.clear();
        }
    }
}
