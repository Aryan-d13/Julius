package com.julius.clipper.config.security.token;

import com.julius.clipper.config.properties.SecurityProperties;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class NimbusTokenIssuer implements TokenIssuer {

    private final SecurityProperties securityProperties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final JWSHeader jwsHeader = new JWSHeader(JWSAlgorithm.HS256);
    private final JWSSigner jwsSigner;

    public NimbusTokenIssuer(SecurityProperties securityProperties) throws KeyLengthException {
        this.securityProperties = securityProperties;
        byte[] decodedKey = Base64.getDecoder().decode(securityProperties.jwt().secret());
        this.jwsSigner = new MACSigner(decodedKey);
    }

    @Override
    public String issueAccessToken(String userId, String sessionId, List<String> roles, Duration expiry) {
        try {
            Date now = new Date();
            Date expiryDate = new Date(now.getTime() + expiry.toMillis());

            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .subject(userId)
                    .claim("sid", sessionId)
                    .claim("roles", roles)
                    .issueTime(now)
                    .expirationTime(expiryDate)
                    .jwtID(UUID.randomUUID().toString())
                    .build();

            SignedJWT signedJWT = new SignedJWT(jwsHeader, claimsSet);
            signedJWT.sign(jwsSigner);
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Failed to generate and sign access token JWT", e);
        }
    }

    @Override
    public String issueRefreshToken() {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
}
