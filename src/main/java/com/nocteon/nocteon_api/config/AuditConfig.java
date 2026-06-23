package com.nocteon.nocteon_api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.web.client.RestTemplate;

import com.nocteon.nocteon_api.auth.config.JwtProperties;

@Configuration
@EnableJpaAuditing
@EnableConfigurationProperties(JwtProperties.class)
public class AuditConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

}
