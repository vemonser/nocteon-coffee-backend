package com.nocteon.nocteon_api.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@ConfigurationProperties(prefix = "application.security.jwt")
@Getter
@Setter
public class JwtProperties {

    private long accessTokenExpiration;
    private long refreshTokenExpiration;
}