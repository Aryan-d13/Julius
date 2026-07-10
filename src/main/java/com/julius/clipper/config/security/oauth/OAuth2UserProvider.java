package com.julius.clipper.config.security.oauth;

import java.util.Map;

public interface OAuth2UserProvider {
    String getProviderName();
    OAuth2UserInfo extractUserInfo(Map<String, Object> attributes);
}
