package com.julius.clipper.config.security.oauth;

import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class GoogleOAuth2UserProvider implements OAuth2UserProvider {

    @Override
    public String getProviderName() {
        return "GOOGLE";
    }

    @Override
    public OAuth2UserInfo extractUserInfo(Map<String, Object> attributes) {
        String id = (String) attributes.get("sub");
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");
        
        Object emailVerifiedObj = attributes.get("email_verified");
        boolean emailVerified = false;
        if (emailVerifiedObj instanceof Boolean) {
            emailVerified = (Boolean) emailVerifiedObj;
        } else if (emailVerifiedObj instanceof String) {
            emailVerified = Boolean.parseBoolean((String) emailVerifiedObj);
        }

        return new OAuth2UserInfo(id, email, name, emailVerified);
    }
}
