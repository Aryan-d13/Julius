package com.julius.clipper.service;

import com.julius.clipper.config.properties.SecurityProperties;
import com.julius.clipper.config.security.oauth.OAuth2UserInfo;
import com.julius.clipper.config.security.oauth.OAuth2UserProvider;
import com.julius.clipper.config.security.token.TokenIssuer;
import com.julius.clipper.domain.*;
import com.julius.clipper.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserProviderAccountRepository providerAccountRepository;
    private final UserSessionRepository sessionRepository;
    private final LoginAuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final SecurityProperties securityProperties;
    private final List<OAuth2UserProvider> oauthProviders;
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserProviderAccountRepository providerAccountRepository,
            UserSessionRepository sessionRepository,
            LoginAuditLogRepository auditLogRepository,
            PasswordEncoder passwordEncoder,
            TokenIssuer tokenIssuer,
            SecurityProperties securityProperties,
            List<OAuth2UserProvider> oauthProviders) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.providerAccountRepository = providerAccountRepository;
        this.sessionRepository = sessionRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.securityProperties = securityProperties;
        this.oauthProviders = oauthProviders;
        // Pre-compute dummy hash at startup to prevent user enumeration timing attacks
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-sidechannel-enumeration-mitigation");
    }

    @Transactional
    public User register(String email, String password, String fullName) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("User with this email already exists");
        }

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("ROLE_USER not seeded in roles table"));

        User user = User.builder()
                .email(email)
                .fullName(fullName)
                .passwordHash(passwordEncoder.encode(password))
                .roles(new HashSet<>(Set.of(userRole)))
                .build();

        return userRepository.save(user);
    }

    @Transactional
    public AuthResponse login(String email, String password, String ip, String userAgent, String correlationId, String requestId) {
        long startTime = System.currentTimeMillis();
        Optional<User> userOpt = userRepository.findByEmail(email);

        boolean userExists = userOpt.isPresent();
        // Match user hash OR fall back to pre-computed dummy hash to match execution timing signature
        String hashToMatch = userExists ? userOpt.get().getPasswordHash() : dummyPasswordHash;
        
        boolean passwordMatches = false;
        if (hashToMatch != null) {
            passwordMatches = passwordEncoder.matches(password, hashToMatch);
        }

        if (!userExists || !passwordMatches) {
            logAudit(null, null, email, "LOGIN_FAILED", ip, userAgent, "Invalid credentials", correlationId, requestId, startTime);
            throw new IllegalArgumentException("Invalid email or password");
        }

        User user = userOpt.get();
        AuthResponse response = createSessionAndIssueTokens(user, ip, userAgent, correlationId, requestId, startTime, "LOGIN_SUCCESS");
        return response;
    }

    @Transactional
    public AuthResponse loginOrLinkOAuth2(String providerName, Map<String, Object> attributes, String ip, String userAgent, String correlationId, String requestId) {
        long startTime = System.currentTimeMillis();
        
        OAuth2UserProvider provider = oauthProviders.stream()
                .filter(p -> p.getProviderName().equalsIgnoreCase(providerName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported OAuth2 provider: " + providerName));

        OAuth2UserInfo userInfo = provider.extractUserInfo(attributes);
        if (!userInfo.emailVerified()) {
            logAudit(null, null, userInfo.email(), "LOGIN_FAILED", ip, userAgent, "Provider email not verified", correlationId, requestId, startTime);
            throw new IllegalArgumentException("Federated email from " + providerName + " must be verified");
        }

        User user;
        Optional<User> existingUserOpt = userRepository.findByEmail(userInfo.email());
        if (existingUserOpt.isPresent()) {
            user = existingUserOpt.get();
            // Link account if not already linked
            Optional<UserProviderAccount> linkedAccountOpt = providerAccountRepository
                    .findByProviderAndProviderUserId(providerName.toUpperCase(), userInfo.id());
            if (linkedAccountOpt.isEmpty()) {
                UserProviderAccount providerAccount = UserProviderAccount.builder()
                        .user(user)
                        .provider(providerName.toUpperCase())
                        .providerUserId(userInfo.id())
                        .build();
                providerAccountRepository.save(providerAccount);
            }
        } else {
            // New user registration via OAuth
            Role userRole = roleRepository.findByName("ROLE_USER")
                    .orElseThrow(() -> new IllegalStateException("ROLE_USER not seeded"));

            user = User.builder()
                    .email(userInfo.email())
                    .fullName(userInfo.name())
                    .roles(new HashSet<>(Set.of(userRole)))
                    .build();
            user = userRepository.save(user);

            UserProviderAccount providerAccount = UserProviderAccount.builder()
                    .user(user)
                    .provider(providerName.toUpperCase())
                    .providerUserId(userInfo.id())
                    .build();
            providerAccountRepository.save(providerAccount);
        }

        return createSessionAndIssueTokens(user, ip, userAgent, correlationId, requestId, startTime, "LOGIN_SUCCESS_OAUTH");
    }

    @Transactional
    public AuthResponse refresh(String refreshTokenValue, String ip, String userAgent, String correlationId, String requestId) {
        long startTime = System.currentTimeMillis();
        String incomingHash = hashToken(refreshTokenValue);
        Optional<UserSession> sessionOpt = sessionRepository.findByTokenHashOrPreviousTokenHash(incomingHash, incomingHash);

        if (sessionOpt.isEmpty()) {
            logAudit(null, null, "unknown", "REFRESH_FAILED", ip, userAgent, "Session token hash not found", correlationId, requestId, startTime);
            throw new IllegalArgumentException("Invalid refresh token");
        }

        UserSession session = sessionOpt.get();

        // Detect refresh token reuse theft: matches previousTokenHash or is already revoked or expired
        boolean isReuse = incomingHash.equals(session.getPreviousTokenHash());
        if (isReuse || session.isRevoked() || session.getExpiresAt().isBefore(LocalDateTime.now())) {
            
            // Mitigate race conditions: if incoming matches previous token hash, check a short 10s grace window
            if (isReuse) {
                boolean withinGracePeriod = session.getLastUsedAt().plusSeconds(10).isAfter(LocalDateTime.now());
                if (withinGracePeriod) {
                    logAudit(session.getUser().getId(), session.getId(), session.getUser().getEmail(),
                             "CONCURRENT_REFRESH_GLITCH", ip, userAgent, "Concurrent refresh request within grace period", correlationId, requestId, startTime);
                    throw new IllegalArgumentException("Concurrent refresh request. Please retry.");
                }
            }

            // Real theft / expired -> Invalidate session tree as threat countermeasure
            List<UserSession> activeSessions = sessionRepository.findByUserIdAndRevokedFalse(session.getUser().getId());
            activeSessions.forEach(s -> s.setRevoked(true));
            sessionRepository.saveAll(activeSessions);

            logAudit(session.getUser().getId(), session.getId(), session.getUser().getEmail(), 
                     "REUSE_ATTEMPT_REVOCATION", ip, userAgent, 
                     isReuse ? "Token reuse detected (replay attack). Revoked session tree." : "Session expired or revoked. Revoked session tree.", 
                     correlationId, requestId, startTime);
            
            throw new IllegalArgumentException("Session expired or token reused. Please sign in again.");
        }

        // Rotate token (RTR)
        String newRefreshToken = tokenIssuer.issueRefreshToken();
        String newHash = hashToken(newRefreshToken);

        session.setPreviousTokenHash(incomingHash);
        session.setTokenHash(newHash);
        session.setRotationCounter(session.getRotationCounter() + 1);
        session.setLastUsedIp(ip);
        session.setLastUsedAt(LocalDateTime.now());
        sessionRepository.save(session);

        List<String> roles = session.getUser().getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList());

        Duration accessExpiry = Duration.ofMillis(securityProperties.jwt().accessExpiryMs());
        String newAccessToken = tokenIssuer.issueAccessToken(
                session.getUser().getId(),
                session.getId(),
                roles,
                accessExpiry
        );

        logAudit(session.getUser().getId(), session.getId(), session.getUser().getEmail(),
                 "REFRESH_SUCCESS", ip, userAgent, null, correlationId, requestId, startTime);

        return new AuthResponse(newAccessToken, newRefreshToken, session.getId());
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        String incomingHash = hashToken(refreshTokenValue);
        sessionRepository.findByTokenHashOrPreviousTokenHash(incomingHash, incomingHash).ifPresent(session -> {
            session.setRevoked(true);
            sessionRepository.save(session);
        });
    }

    /**
     * Creates session and issues tokens.
     * Note: Refresh tokens generated via tokenIssuer are cryptographically secure random bytes
     * (providing at least 256 bits of entropy) Base64 encoded.
     */
    private AuthResponse createSessionAndIssueTokens(User user, String ip, String userAgent, String correlationId, String requestId, long startTime, String eventType) {
        String sessionId = UUID.randomUUID().toString();
        String refreshTokenValue = tokenIssuer.issueRefreshToken();
        String tokenHash = hashToken(refreshTokenValue);

        // Simple JSON client metadata
        String clientMetadata = String.format(
                "{\"device_id\":\"%s\",\"browser\":\"%s\",\"platform\":\"Web\"}",
                UUID.randomUUID(),
                userAgent != null && userAgent.length() > 50 ? userAgent.substring(0, 50) : userAgent
        );

        LocalDateTime expiresAt = LocalDateTime.now().plus(Duration.ofMillis(securityProperties.jwt().refreshExpiryMs()));

        UserSession session = UserSession.builder()
                .id(sessionId)
                .user(user)
                .tokenHash(tokenHash)
                .clientMetadata(clientMetadata)
                .createdIp(ip)
                .lastUsedIp(ip)
                .expiresAt(expiresAt)
                .build();

        sessionRepository.save(session);

        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList());

        Duration accessExpiry = Duration.ofMillis(securityProperties.jwt().accessExpiryMs());
        String accessToken = tokenIssuer.issueAccessToken(user.getId(), sessionId, roles, accessExpiry);

        logAudit(user.getId(), sessionId, user.getEmail(), eventType, ip, userAgent, null, correlationId, requestId, startTime);

        return new AuthResponse(accessToken, refreshTokenValue, sessionId);
    }

    private void logAudit(String userId, String sessionId, String email, String eventType, String ip, String userAgent, String failureReason, String correlationId, String requestId) {
        logAudit(userId, sessionId, email, eventType, ip, userAgent, failureReason, correlationId, requestId, System.currentTimeMillis());
    }

    private void logAudit(String userId, String sessionId, String email, String eventType, String ip, String userAgent, String failureReason, String correlationId, String requestId, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        LoginAuditLog auditLog = LoginAuditLog.builder()
                .userId(userId)
                .sessionId(sessionId)
                .email(email)
                .eventType(eventType)
                .ipAddress(ip != null ? ip : "unknown")
                .userAgent(userAgent)
                .failureReason(failureReason)
                .processingTimeMs(duration)
                .correlationId(correlationId != null ? correlationId : "N/A")
                .requestId(requestId != null ? requestId : "N/A")
                .build();
        auditLogRepository.save(auditLog);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash token value", e);
        }
    }

    public static record AuthResponse(
        String accessToken,
        String refreshToken,
        String sessionId
    ) {}
}
