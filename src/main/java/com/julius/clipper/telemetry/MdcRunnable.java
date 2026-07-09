package com.julius.clipper.telemetry;

import org.slf4j.MDC;
import java.util.Map;

public class MdcRunnable implements Runnable {
    private final Runnable delegate;
    private final Map<String, String> contextMap;

    public MdcRunnable(Runnable delegate) {
        this.delegate = delegate;
        this.contextMap = MDC.getCopyOfContextMap();
    }

    @Override
    public void run() {
        Map<String, String> oldContext = MDC.getCopyOfContextMap();
        if (contextMap != null) {
            MDC.setContextMap(contextMap);
        } else {
            MDC.clear();
        }
        try {
            delegate.run();
        } finally {
            if (oldContext != null) {
                MDC.setContextMap(oldContext);
            } else {
                MDC.clear();
            }
        }
    }
}
