package com.nocteon.nocteon_api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.auth.dto.response.AuthResponse;
import com.nocteon.nocteon_api.auth.oauth2.OAuth2CodeService;
import com.nocteon.nocteon_api.auth.service.AuthService;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidVerificationException;
import com.nocteon.nocteon_api.support.TestJwtKeys;

@SpringBootTest(classes = NocteonApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OAuth2ExchangeIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private OAuth2CodeService codeService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Test
    void exchangeReturnsAnAccessTokenAndConsumesTheCode() {
        String username = "oauth_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        jdbcTemplate.update("""
                insert into users (username, email, password, enabled, provider, role, is_active, created_at, updated_at)
                values (?, ?, ?, true, 'LOCAL', 'CUSTOMER', true, now(), now())
                """, username, username + "@example.com", passwordEncoder.encode("Xk9#mP2$vL7@qR4!"));
        Long userId = jdbcTemplate.queryForObject(
                "select id from users where username = ?", Long.class, username);
        try {
            String code = codeService.issue(userId);

            AuthResponse response = authService.exchangeOAuth2Code(code);

            assertThat(response.getAccessToken()).isNotBlank();
            assertThat(response.getUser().getId()).isEqualTo(userId);
            assertThatThrownBy(() -> authService.exchangeOAuth2Code(code))
                    .isInstanceOf(InvalidVerificationException.class);
        } finally {
            jdbcTemplate.update("delete from users where username = ?", username);
        }
    }
}
