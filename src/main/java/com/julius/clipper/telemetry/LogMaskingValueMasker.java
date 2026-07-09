package com.julius.clipper.telemetry;

import com.fasterxml.jackson.core.JsonStreamContext;
import net.logstash.logback.mask.ValueMasker;
import java.util.regex.Pattern;

public class LogMaskingValueMasker implements ValueMasker {
    private static final Pattern[] MASK_PATTERNS = {
        Pattern.compile("google\\.api\\.key\\s*[=:]\\s*([^,\\s\"}]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("password\\s*[=:]\\s*([^,\\s\"}]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("cookies?\\s*[=:]\\s*([^,\\s\"}]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("bearer\\s+([^,\\s\"}]+)", Pattern.CASE_INSENSITIVE)
    };

    @Override
    public Object mask(JsonStreamContext context, Object value) {
        if (value instanceof String str) {
            String masked = str;
            for (Pattern pattern : MASK_PATTERNS) {
                masked = pattern.matcher(masked).replaceAll(mr -> {
                    String matched = mr.group();
                    String secret = mr.group(1);
                    return matched.replace(secret, "******");
                });
            }
            return masked;
        }
        return value;
    }
}
