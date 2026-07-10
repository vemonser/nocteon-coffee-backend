package com.nocteon.nocteon_api.mail.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class BrevoConfig {

    @Value("${application.brevo.api-key}")
    private String apiKey;

    @Value("${application.brevo.base-url}")
    private String baseUrl;

    @Bean
    public WebClient brevoWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("api-key", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }
}