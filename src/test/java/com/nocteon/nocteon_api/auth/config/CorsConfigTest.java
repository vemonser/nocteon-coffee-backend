package com.nocteon.nocteon_api.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

class CorsConfigTest {

    private final CorsConfig config = new CorsConfig();

    @Test
    void trimsOriginsAndDropsBlankEntries() {
        ReflectionTestUtils.setField(config, "allowedOrigins",
                " https://app.example.com , ,https://admin.example.com ");

        var source = config.corsConfigurationSource();
        var cors = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/settings"));

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins())
                .containsExactly("https://app.example.com", "https://admin.example.com");
    }

    @Test
    void rejectsAnEmptyOriginListInsteadOfAllowingNothingSilently() {
        ReflectionTestUtils.setField(config, "allowedOrigins", " , ");

        assertThatThrownBy(config::corsConfigurationSource)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ALLOWED_ORIGINS");
    }
}
