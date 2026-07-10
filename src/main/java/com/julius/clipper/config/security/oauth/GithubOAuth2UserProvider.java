package com.julius.clipper.config.security.oauth;

import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class GithubOAuth2UserProvider implements OAuth2UserProvider {

    @Override
    public String getProviderName() {
        return "GITHUB";
    }

    @Override
    public OAuth2UserInfo extractUserInfo(Map<String, Object> attributes) {
        Object idObj = attributes.get("id");
        String id = idObj != null ? idObj.toString() : null;
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");
        if (name == null || name.isBlank()) {
            name = (String) attributes.get("login");
        }

        // GitHub doesn't return email_verified in standard attributes, so we check if email is present.
        // For production, we can call the GitHub /user/emails API or trust verified claims if configured.
        boolean emailVerified = (email != null && !email.isBlank());

        return new OAuth2UserInfo(id, email, name, emailVerified);
    }
}
