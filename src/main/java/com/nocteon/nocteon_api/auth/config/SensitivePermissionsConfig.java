package com.nocteon.nocteon_api.auth.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SensitivePermissionsConfig {
    @Bean
    public List<String> sensitivePermissions() {
        return List.of(
            "user:change_role",
            "user:delete",
            "settings:update",
            "audit:read",
            "ai:manage",
            "role:manage"
        );
    }
}
