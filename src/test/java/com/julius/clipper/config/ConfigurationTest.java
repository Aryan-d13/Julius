package com.julius.clipper.config;

import com.julius.clipper.config.properties.*;
import com.julius.clipper.config.validation.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Map;
import java.util.SortedMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConfigurationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final BeanValidator beanValidator = new BeanValidator(validator);
    private final StoragePropertiesValidator storageValidator = new StoragePropertiesValidator();
    private final QueuePropertiesValidator queueValidator = new QueuePropertiesValidator();

    @Test
    public void testJsr380ValidationConstraints() {
        // Invalid worker concurrency (below 1)
        WorkerProperties workerProps = new WorkerProperties(0, 2, 1);
        ConfigurationValidator configValidator = new ConfigurationValidator(
                null, null, null, null, null, workerProps, null, null, null,
                storageValidator, queueValidator, beanValidator
        );

        assertThatThrownBy(configValidator::validateAll)
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessageContaining("ioConcurrencyLimit");
    }

    @Test
    public void testStorageConditionalValidationLocal() {
        // local storage type with blank root
        StorageProperties storageProps = new StorageProperties("local", new StorageProperties.Local("  "), null);
        
        assertThatThrownBy(() -> storageValidator.validate(storageProps))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessageContaining("local.root: must not be blank");
    }

    @Test
    public void testStorageConditionalValidationGcs() {
        // gcs storage type with blank bucket
        StorageProperties storageProps = new StorageProperties("gcs", null, new StorageProperties.Gcs(""));
        
        assertThatThrownBy(() -> storageValidator.validate(storageProps))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessageContaining("gcs.bucket: must not be blank");
    }

    @Test
    public void testQueueConditionalValidationRedis() {
        // redis queue type with blank host
        QueueProperties queueProps = new QueueProperties("redis", new QueueProperties.Redis("", 6379, null));
        
        assertThatThrownBy(() -> queueValidator.validate(queueProps))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessageContaining("redis.host: must not be blank");
    }

    @Test
    public void testQueueConditionalValidationRedisNullPort() {
        // redis queue type with null port
        QueueProperties queueProps = new QueueProperties("redis", new QueueProperties.Redis("localhost", null, null));
        
        assertThatThrownBy(() -> queueValidator.validate(queueProps))
                .isInstanceOf(ConfigurationValidationException.class)
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
        QueueProperties queue = new QueueProperties("db", new QueueProperties.Redis("localhost", 6379, "super-secret-password"));
        AiProperties ai = new AiProperties("secret-key-123", "gemini-1.5-flash", new AiProperties.Whisper("large-v3-turbo", "python", ""));
        TelemetryProperties telemetry = new TelemetryProperties("test", new TelemetryProperties.Otlp("http://localhost", 1.0));
        SecurityProperties security = new SecurityProperties(false, "*", new SecurityProperties.Jwt("Y2xpcHBlci1zZWN1cml0eS1qd3Qtc2VjcmV0LWtleS1kZXYtcGxhdGZvcm0tc3VwZXItc3Ryb25nLWtleQ==", 900000, 604800000));
        WorkerProperties worker = new WorkerProperties(8, 2, 1);
        DownloadProperties download = new DownloadProperties("dir", "", "format");
        WorkspaceProperties workspace = new WorkspaceProperties("d", "d", "d", "d", "d", "d", "d");
        FeatureFlagsProperties featureFlags = new FeatureFlagsProperties(Map.of("new-ui-enabled", true));

        ConfigurationSummaryLogger logger = new ConfigurationSummaryLogger(env, storage, queue, ai, telemetry, security, worker, download, workspace, featureFlags, null);

        SortedMap<String, String> schema1 = logger.getNormalizedModelSchema();
        assertThat(schema1.get("ai.gemini-api-key")).isEqualTo("<secret>");
        assertThat(schema1.get("queue.redis.password")).isEqualTo("<secret>");
        assertThat(schema1.get("security.jwt.secret")).isEqualTo("<secret>");
        assertThat(schema1.get("ai.gemini-model")).isEqualTo("gemini-1.5-flash");
        assertThat(schema1.get("features.new-ui-enabled")).isEqualTo("true");

        // Calculate fingerprints
        String fp1 = logger.calculateFingerprint(schema1);
        
        // Change secret values only (should NOT change fingerprint because both are represented by '<secret>')
        QueueProperties queueDifferentPass = new QueueProperties("db", new QueueProperties.Redis("localhost", 6379, "different-password"));
        AiProperties aiDifferentKey = new AiProperties("different-key", "gemini-1.5-flash", new AiProperties.Whisper("large-v3-turbo", "python", ""));
        
        ConfigurationSummaryLogger logger2 = new ConfigurationSummaryLogger(env, storage, queueDifferentPass, aiDifferentKey, telemetry, security, worker, download, workspace, featureFlags, null);
        SortedMap<String, String> schema2 = logger2.getNormalizedModelSchema();
        String fp2 = logger2.calculateFingerprint(schema2);

        // Secrets must NOT change fingerprint
        assertThat(fp1).isEqualTo(fp2);

        // Remove secret (should change fingerprint because key goes from '<secret>' to 'null')
        QueueProperties queueNoPass = new QueueProperties("db", new QueueProperties.Redis("localhost", 6379, null));
        ConfigurationSummaryLogger logger3 = new ConfigurationSummaryLogger(env, storage, queueNoPass, aiDifferentKey, telemetry, security, worker, download, workspace, featureFlags, null);
        SortedMap<String, String> schema3 = logger3.getNormalizedModelSchema();
        String fp3 = logger3.calculateFingerprint(schema3);

        assertThat(fp1).isNotEqualTo(fp3);
    }
}
