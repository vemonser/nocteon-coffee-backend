package com.nocteon.nocteon_api.cloudinary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@ConfigurationProperties(prefix = "application.cloudinary")
@Getter
@Setter
public class CloudinaryProperties {
    private String cloudName;
    private String apiKey;
    private String apiSecret;
}