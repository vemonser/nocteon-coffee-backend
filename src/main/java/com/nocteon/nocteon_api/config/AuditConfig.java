package com.nocteon.nocteon_api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import com.nocteon.nocteon_api.auth.config.JwtProperties;

@Configuration
@EnableJpaAuditing
@EnableConfigurationProperties(JwtProperties.class)
public class AuditConfig {
    
}
