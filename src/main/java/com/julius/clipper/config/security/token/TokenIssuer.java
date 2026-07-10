package com.julius.clipper.config.security.token;

import java.time.Duration;
import java.util.List;

public interface TokenIssuer {
    String issueAccessToken(String userId, String sessionId, List<String> roles, Duration expiry);
    String issueRefreshToken();
}
