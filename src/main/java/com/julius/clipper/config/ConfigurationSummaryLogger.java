package com.julius.clipper.config;

import com.julius.clipper.config.properties.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
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
    private final SecurityProperties securityProperties;
    private final WorkerProperties workerProperties;
    private final DownloadProperties downloadProperties;
    private final WorkspaceProperties workspaceProperties;
    private final FeatureFlagsProperties featureFlagsProperties;
    
    private final BuildProperties buildProperties;

    public ConfigurationSummaryLogger(
            Environment environment,
            StorageProperties storageProperties,
            QueueProperties queueProperties,
            AiProperties aiProperties,
            TelemetryProperties telemetryProperties,
            SecurityProperties securityProperties,
            WorkerProperties workerProperties,
            DownloadProperties downloadProperties,
            WorkspaceProperties workspaceProperties,
            FeatureFlagsProperties featureFlagsProperties,
            @Autowired(required = false) BuildProperties buildProperties) {
        this.environment = environment;
        this.storageProperties = storageProperties;
        this.queueProperties = queueProperties;
        this.aiProperties = aiProperties;
        this.telemetryProperties = telemetryProperties;
        this.securityProperties = securityProperties;
        this.workerProperties = workerProperties;
        this.downloadProperties = downloadProperties;
        this.workspaceProperties = workspaceProperties;
        this.featureFlagsProperties = featureFlagsProperties;
        this.buildProperties = buildProperties;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        logStartupSummary();
    }

    public void logStartupSummary() {
        String version = buildProperties != null ? buildProperties.getVersion() : "unknown";
        String activeProfiles = Arrays.toString(environment.getActiveProfiles());
        
        String storageType = storageProperties.type();
        String queueType = queueProperties.type();
        String telemetryEnv = telemetryProperties.env();
        
        String otlpEndpoint = telemetryProperties.otlp() != null ? telemetryProperties.otlp().endpoint() : "N/A";
        double samplingRatio = telemetryProperties.otlp() != null ? telemetryProperties.otlp().samplingRatio() : 0.0;
        String otlpState = otlpEndpoint + " (sample ratio: " + samplingRatio + ")";
        
        String whisperModel = aiProperties.whisper() != null ? aiProperties.whisper().model() : "N/A";
        String aiState = "Gemini: " + aiProperties.geminiModel() + " / Whisper: " + whisperModel;

        SortedMap<String, String> schema = getNormalizedModelSchema();
        String fingerprint = calculateFingerprint(schema);

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
        log.info("Config Fingerprint  : {}", fingerprint);
        log.info("====================================================================");

        // Move detailed property dumps to DEBUG level only
        if (log.isDebugEnabled()) {
            log.debug("Effective Sanitized Configuration Model Details:");
            schema.forEach((key, value) -> log.debug("  {} = {}", key, value));
        }
    }

    public SortedMap<String, String> getNormalizedModelSchema() {
        SortedMap<String, String> schema = new TreeMap<>();
        
        // 1. Storage
        schema.put("storage.type", storageProperties.type());
        schema.put("storage.local.root", storageProperties.local() != null ? storageProperties.local().root() : "null");
        schema.put("storage.gcs.bucket", storageProperties.gcs() != null ? storageProperties.gcs().bucket() : "null");
        
        // Workspace Directories
        if (workspaceProperties != null) {
            schema.put("workspace.download-dir", workspaceProperties.downloadDir());
            schema.put("workspace.convert-dir", workspaceProperties.convertDir());
            schema.put("workspace.cut-dir", workspaceProperties.cutDir());
            schema.put("workspace.cache-dir", workspaceProperties.cacheDir());
            schema.put("workspace.video-library-dir", workspaceProperties.videoLibraryDir());
            schema.put("workspace.audio-library-dir", workspaceProperties.audioLibraryDir());
            schema.put("workspace.render-output-dir", workspaceProperties.renderOutputDir());
        }
        
        // 2. Queue
        schema.put("queue.type", queueProperties.type());
        schema.put("queue.redis.host", queueProperties.redis() != null ? queueProperties.redis().host() : "null");
        schema.put("queue.redis.port", queueProperties.redis() != null && queueProperties.redis().port() != null ? String.valueOf(queueProperties.redis().port()) : "null");
        schema.put("queue.redis.password", queueProperties.redis() != null && queueProperties.redis().password() != null && !queueProperties.redis().password().isBlank() ? "<secret>" : "null");

        // 3. AI
        schema.put("ai.gemini-model", aiProperties.geminiModel());
        schema.put("ai.gemini-api-key", aiProperties.geminiApiKey() != null && !aiProperties.geminiApiKey().isBlank() ? "<secret>" : "null");
        schema.put("ai.whisper.model", aiProperties.whisper() != null ? aiProperties.whisper().model() : "null");
        schema.put("ai.whisper.python-path", aiProperties.whisper() != null ? aiProperties.whisper().pythonPath() : "null");
        schema.put("ai.whisper.python-env", aiProperties.whisper() != null && aiProperties.whisper().pythonEnv() != null ? aiProperties.whisper().pythonEnv() : "null");

        // 4. Telemetry
        schema.put("telemetry.env", telemetryProperties.env());
        schema.put("telemetry.otlp.endpoint", telemetryProperties.otlp() != null ? telemetryProperties.otlp().endpoint() : "null");
        schema.put("telemetry.otlp.sampling-ratio", telemetryProperties.otlp() != null ? String.valueOf(telemetryProperties.otlp().samplingRatio()) : "null");

        // 5. Security
        schema.put("security.cors-enabled", String.valueOf(securityProperties.corsEnabled()));
        schema.put("security.allowed-origins", securityProperties.allowedOrigins());

        // 6. Worker
        schema.put("worker.io-concurrency-limit", String.valueOf(workerProperties.ioConcurrencyLimit()));
        schema.put("worker.cpu-concurrency-limit", String.valueOf(workerProperties.cpuConcurrencyLimit()));
        schema.put("worker.gpu-concurrency-limit", String.valueOf(workerProperties.gpuConcurrencyLimit()));

        // 7. Download
        if (downloadProperties != null) {
            schema.put("download.dir", downloadProperties.dir());
            schema.put("download.cookies-path", downloadProperties.cookiesPath() != null && !downloadProperties.cookiesPath().isBlank() ? downloadProperties.cookiesPath() : "null");
            schema.put("download.format", downloadProperties.format());
        }

        // 8. Feature Flags
        if (featureFlagsProperties != null && featureFlagsProperties.features() != null) {
            featureFlagsProperties.features().forEach((key, value) -> {
                schema.put("features." + key, String.valueOf(value));
            });
        }

        return schema;
    }

    public String calculateFingerprint(Map<String, String> properties) {
        try {
            StringBuilder sb = new StringBuilder();
            new TreeMap<>(properties).forEach((key, value) -> {
                sb.append(key).append("=").append(value).append("\n");
            });

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
