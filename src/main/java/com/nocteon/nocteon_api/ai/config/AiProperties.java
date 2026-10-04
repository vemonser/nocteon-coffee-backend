package com.nocteon.nocteon_api.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Feature flags for the AI capabilities. All three default to {@code false} so a
 * deployment without Ollama / a populated vector store behaves exactly as it did
 * before AI was introduced.
 *
 * <p>Registered by {@link AiPropertiesConfig} via
 * {@code @EnableConfigurationProperties}.
 *
 * <p>Getters are Lombok-generated for {@code boolean} fields, so they read
 * {@code isEnabled()}, {@code isSearchEnabled()} and {@code isChatbotEnabled()} —
 * not {@code getEnabled()}. Callers depend on those exact names.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "nocteon.ai")
public class AiProperties {

    /** Master switch for the AI subsystem. */
    private boolean enabled;

    /** Enables AI-powered semantic product search. */
    private boolean searchEnabled;

    /** Enables the AI shopping assistant. */
    private boolean chatbotEnabled;
}