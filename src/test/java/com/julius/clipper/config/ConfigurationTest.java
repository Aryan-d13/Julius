package com.julius.clipper.config;

import com.julius.clipper.config.properties.*;
import com.julius.clipper.config.validation.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConfigurationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final StoragePropertiesValidator storageValidator = new StoragePropertiesValidator();
    private final QueuePropertiesValidator queueValidator = new QueuePropertiesValidator();

    @Test
    public void testJsr380ValidationConstraints() {
        // Invalid worker concurrency (below 1)
        WorkerProperties workerProps = new WorkerProperties(0, 2, 1);
        ConfigurationValidator configValidator = new ConfigurationValidator(
                null, null, null, null, null, workerProps,
                storageValidator, queueValidator, validator
        );

        assertThatThrownBy(configValidator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ioConcurrencyLimit");
    }

    @Test
    public void testStorageConditionalValidationLocal() {
        // local storage type with blank root
        StorageProperties storageProps = new StorageProperties("local", new StorageProperties.Local("  "), null);
        
        assertThatThrownBy(() -> storageValidator.validate(storageProps))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local.root: must not be blank");
    }

    @Test
    public void testStorageConditionalValidationGcs() {
        // gcs storage type with blank bucket
        StorageProperties storageProps = new StorageProperties("gcs", null, new StorageProperties.Gcs(""));
        
        assertThatThrownBy(() -> storageValidator.validate(storageProps))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gcs.bucket: must not be blank");
    }

    @Test
    public void testQueueConditionalValidationRedis() {
        // redis queue type with blank host
        QueueProperties queueProps = new QueueProperties("redis", new QueueProperties.Redis("", 6379, null));
        
        assertThatThrownBy(() -> queueValidator.validate(queueProps))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("redis.host: must not be blank");
    }

    @Test
    public void testQueueConditionalValidationRedisNullPort() {
        // redis queue type with null port
        QueueProperties queueProps = new QueueProperties("redis", new QueueProperties.Redis("localhost", null, null));
        
        assertThatThrownBy(() -> queueValidator.validate(queueProps))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("redis.port: must not be null");
    }

    @Test
    public void testMaskingAndFingerprinting() {
        StandardEnvironment env = new StandardEnvironment();
        Map<String, Object> testProps = Map.of(
                "clipper.ai.gemini-api-key", "secret-key-123",
                "clipper.ai.gemini-model", "gemini-1.5-flash",
                "clipper.queue.redis.password", "super-secret-password",
                "clipper.storage.type", "local"
        );
        env.getPropertySources().addFirst(new MapPropertySource("testSource", testProps));

        StorageProperties storage = new StorageProperties("local", new StorageProperties.Local("data/storage"), null);
        QueueProperties queue = new QueueProperties("db", null);
        AiProperties ai = new AiProperties("secret-key-123", "gemini-1.5-flash", null);
        TelemetryProperties telemetry = new TelemetryProperties("test", null);

        ConfigurationSummaryLogger logger = new ConfigurationSummaryLogger(env, storage, queue, ai, telemetry);

        Map<String, String> sanitized = logger.getSanitizedClipperProperties();
        assertThat(sanitized.get("clipper.ai.gemini-api-key")).isEqualTo("[MASKED]");
        assertThat(sanitized.get("clipper.queue.redis.password")).isEqualTo("[MASKED]");
        assertThat(sanitized.get("clipper.ai.gemini-model")).isEqualTo("gemini-1.5-flash");

        // Calculate fingerprints
        String fp1 = logger.calculateFingerprint(sanitized);
        
        // Change secret values only
        Map<String, Object> testProps2 = Map.of(
                "clipper.ai.gemini-api-key", "different-secret-key-456",
                "clipper.ai.gemini-model", "gemini-1.5-flash",
                "clipper.queue.redis.password", "another-password",
                "clipper.storage.type", "local"
        );
        StandardEnvironment env2 = new StandardEnvironment();
        env2.getPropertySources().addFirst(new MapPropertySource("testSource2", testProps2));
        
        ConfigurationSummaryLogger logger2 = new ConfigurationSummaryLogger(env2, storage, queue, ai, telemetry);
        Map<String, String> sanitized2 = logger2.getSanitizedClipperProperties();
        String fp2 = logger2.calculateFingerprint(sanitized2);

        // Secrets must NOT change fingerprint
        assertThat(fp1).isEqualTo(fp2);

        // Change normal property (e.g. type from local to gcs)
        Map<String, Object> testProps3 = Map.of(
                "clipper.ai.gemini-api-key", "secret-key-123",
                "clipper.ai.gemini-model", "gemini-1.5-flash",
                "clipper.queue.redis.password", "super-secret-password",
                "clipper.storage.type", "gcs"
        );
        StandardEnvironment env3 = new StandardEnvironment();
        env3.getPropertySources().addFirst(new MapPropertySource("testSource3", testProps3));
        
        ConfigurationSummaryLogger logger3 = new ConfigurationSummaryLogger(env3, storage, queue, ai, telemetry);
        Map<String, String> sanitized3 = logger3.getSanitizedClipperProperties();
        String fp3 = logger3.calculateFingerprint(sanitized3);

        // Normal configuration change MUST modify fingerprint
        assertThat(fp1).isNotEqualTo(fp3);
    }
}
