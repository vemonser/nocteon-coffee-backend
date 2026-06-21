
package com.nocteon.nocteon_api.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    JwtProperties.class,
    // LockoutProperties.class,
    // VerificationProperties.class
})
public class AuthPropertiesConfig {
}