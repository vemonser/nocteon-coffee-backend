package com.nocteon.nocteon_api.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link AiProperties} for binding.
 *
 * <p>Deliberately not a class-wide {@code @ConfigurationPropertiesScan}: three other
 * properties classes in this project are already registered via
 * {@code @EnableConfigurationProperties}, and scanning would register them a second time.
 * This mirrors the existing {@code AuthPropertiesConfig} pattern instead.
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiPropertiesConfig {
}