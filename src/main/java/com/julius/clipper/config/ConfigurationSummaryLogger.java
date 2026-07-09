package com.julius.clipper.config;

import com.julius.clipper.config.properties.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.*;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Component
public class ConfigurationSummaryLogger implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationSummaryLogger.class);

    private final Environment environment;
    private final StorageProperties storageProperties;
    private final QueueProperties queueProperties;
    private final AiProperties aiProperties;
    private final TelemetryProperties telemetryProperties;
    
    private static final List<String> SENSITIVE_KEYWORDS = List.of(
            "password", "api-key", "secret", "token", "credential", "private-key"
    );

    public ConfigurationSummaryLogger(
            Environment environment,
            StorageProperties storageProperties,
            QueueProperties queueProperties,
            AiProperties aiProperties,
            TelemetryProperties telemetryProperties) {
        this.environment = environment;
        this.storageProperties = storageProperties;
        this.queueProperties = queueProperties;
        this.aiProperties = aiProperties;
        this.telemetryProperties = telemetryProperties;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        logStartupSummary();
    }

    public void logStartupSummary() {
        String version = environment.getProperty("info.app.version", "1.0.0-SNAPSHOT");
        String activeProfiles = Arrays.toString(environment.getActiveProfiles());
        
        String storageType = storageProperties.type();
        String queueType = queueProperties.type();
        String telemetryEnv = telemetryProperties.env();
        
        String otlpEndpoint = telemetryProperties.otlp() != null ? telemetryProperties.otlp().endpoint() : "N/A";
        double samplingRatio = telemetryProperties.otlp() != null ? telemetryProperties.otlp().samplingRatio() : 0.0;
        String otlpState = otlpEndpoint + " (sample ratio: " + samplingRatio + ")";
        
        String whisperModel = aiProperties.whisper() != null ? aiProperties.whisper().model() : "N/A";
        String aiState = "Gemini: " + aiProperties.geminiModel() + " / Whisper: " + whisperModel;

        Map<String, String> resolvedClipperProperties = getSanitizedClipperProperties();
        long featureFlagsCount = resolvedClipperProperties.keySet().stream()
                .filter(k -> k.startsWith("clipper.features."))
                .count();

        String fingerprint = calculateFingerprint(resolvedClipperProperties);

        log.info("====================================================================");
        log.info("                 JULIUS STARTUP CONFIGURATION REPORT                 ");
        log.info("====================================================================");
        log.info("Application Version : {}", version);
        log.info("Active Profiles     : {}", activeProfiles);
        log.info("Telemetry Env       : {}", telemetryEnv);
        log.info("Storage Provider    : {}", storageType);
        log.info("Queue Provider      : {}", queueType);
        log.info("Telemetry State     : {}", otlpState);
        log.info("AI Provider         : {}", aiState);
        log.info("Feature Flags Count : {}", featureFlagsCount);
        log.info("Config Fingerprint  : {}", fingerprint);
        log.info("--------------------------------------------------------------------");
        log.info("Effective Sanitized Properties:");
        resolvedClipperProperties.keySet().stream().sorted().forEach(key -> {
            String value = resolvedClipperProperties.get(key);
            log.info("  {} = {}", key, value);
        });
        log.info("====================================================================");
    }

    public Map<String, String> getSanitizedClipperProperties() {
        Map<String, String> properties = new TreeMap<>();
        if (environment instanceof ConfigurableEnvironment) {
            for (PropertySource<?> source : ((ConfigurableEnvironment) environment).getPropertySources()) {
                if (source instanceof EnumerablePropertySource) {
                    for (String name : ((EnumerablePropertySource<?>) source).getPropertyNames()) {
                        if (name.startsWith("clipper.")) {
                            String value = environment.getProperty(name);
                            if (value != null) {
                                if (isSensitiveKey(name)) {
                                    properties.put(name, "[MASKED]");
                                } else {
                                    properties.put(name, value);
                                }
                            }
                        }
                    }
                }
            }
        }
        return properties;
    }

    private boolean isSensitiveKey(String key) {
        String lowerKey = key.toLowerCase();
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (lowerKey.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public String calculateFingerprint(Map<String, String> properties) {
        try {
            // Filter out masked properties from fingerprint calculation to never include secrets
            SortedMap<String, String> fingerprintProps = new TreeMap<>();
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                if (!"[MASKED]".equals(entry.getValue())) {
                    fingerprintProps.put(entry.getKey(), entry.getValue());
                }
            }

            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : fingerprintProps.entrySet()) {
                sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to calculate configuration fingerprint", e);
            return "UNKNOWN";
        }
    }
}
