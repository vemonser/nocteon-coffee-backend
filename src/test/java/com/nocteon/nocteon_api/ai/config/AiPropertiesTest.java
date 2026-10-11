package com.nocteon.nocteon_api.ai.config;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the contract Tasks 4 and 7 compile against: relaxed binding of
 * {@code search-enabled} onto {@code searchEnabled}, and Lombok's boolean getter names
 * ({@code isXxx}, not {@code getXxx}). Uses a plain {@link Binder} so no Spring context,
 * database or Redis is required.
 */
class AiPropertiesTest {

    private AiProperties bind(Map<String, String> source) {
        // An empty source binds nothing, which is exactly the "unconfigured" case: the
        // freshly constructed instance must already carry the disabled defaults.
        return new Binder(new MapConfigurationPropertySource(source))
                .bind("nocteon.ai", AiProperties.class)
                .orElseGet(AiProperties::new);
    }

    @Test
    void allFlagsDefaultToFalseWhenNothingIsConfigured() {
        AiProperties properties = bind(Map.of());

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.isSearchEnabled()).isFalse();
        assertThat(properties.isChatbotEnabled()).isFalse();
    }

    @Test
    void bindsEveryFlagFromTheApplicationYamlKeys() {
        AiProperties properties = bind(Map.of(
                "nocteon.ai.enabled", "true",
                "nocteon.ai.search-enabled", "true",
                "nocteon.ai.chatbot-enabled", "true"));

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.isSearchEnabled()).isTrue();
        assertThat(properties.isChatbotEnabled()).isTrue();
    }

    @Test
    void flagsAreIndependent() {
        AiProperties properties = bind(Map.of("nocteon.ai.search-enabled", "true"));

        assertThat(properties.isSearchEnabled()).isTrue();
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.isChatbotEnabled()).isFalse();
    }
}