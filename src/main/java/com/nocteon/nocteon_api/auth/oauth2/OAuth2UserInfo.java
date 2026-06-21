package com.nocteon.nocteon_api.auth.oauth2;

public interface OAuth2UserInfo {
    String getId();
    String getEmail();
    String getName();
    String getFirstName();
    String getLastName();
    String getAvatarUrl();
}