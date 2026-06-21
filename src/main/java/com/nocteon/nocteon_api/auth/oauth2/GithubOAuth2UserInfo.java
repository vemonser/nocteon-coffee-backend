package com.nocteon.nocteon_api.auth.oauth2;

import java.util.Map;

public class GithubOAuth2UserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;

    public GithubOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public String getId() {
        return String.valueOf(attributes.get("id"));
    }

    @Override
    public String getEmail() {
        return (String) attributes.get("email");
    }

    @Override
    public String getName() {
        return (String) attributes.get("login");
    }

    @Override
    public String getFirstName() {
        String name = (String) attributes.get("name");
        if (name == null)
            return null;
        String[] parts = name.split(" ", 2);
        return parts[0];
    }

    @Override
    public String getLastName() {
        String name = (String) attributes.get("name");
        if (name == null)
            return null;
        String[] parts = name.split(" ", 2);
        return parts.length > 1 ? parts[1] : null;
    }

    @Override
    public String getAvatarUrl() {
        return (String) attributes.get("avatar_url");
    }
}