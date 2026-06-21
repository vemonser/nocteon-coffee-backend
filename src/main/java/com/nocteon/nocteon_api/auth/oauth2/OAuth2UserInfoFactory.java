package com.nocteon.nocteon_api.auth.oauth2;

import java.util.Map;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import com.nocteon.nocteon_api.auth.enums.Provider;

public class OAuth2UserInfoFactory {

    public static OAuth2UserInfo getOAuth2UserInfo(Provider provider, Map<String, Object> attributes) {
        return switch (provider) {
            case GOOGLE -> new GoogleOAuth2UserInfo(attributes);
            case GITHUB -> new GithubOAuth2UserInfo(attributes);
            default -> throw new OAuth2AuthenticationException("Unsupported provider: " + provider);
        };
    }
}