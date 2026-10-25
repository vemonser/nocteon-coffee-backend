package com.nocteon.nocteon_api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.auth.dto.request.LoginRequest;
import com.nocteon.nocteon_api.auth.dto.response.AuthResult;
import com.nocteon.nocteon_api.auth.service.AuthService;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidRefreshTokenException;
import com.nocteon.nocteon_api.common.util.HashUtil;
import com.nocteon.nocteon_api.support.TestJwtKeys;

@SpringBootTest(classes = NocteonApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RefreshTokenRotationIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    private String testUsername() {
        return "rttest_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private long createTestUser(String username, String rawPassword) {
        String hash = passwordEncoder.encode(rawPassword);
        jdbcTemplate.update(
                "insert into users (username, email, password, enabled, provider, role, is_active, created_at, updated_at) " +
                        "values (?, ?, ?, true, 'LOCAL', 'CUSTOMER', true, now(), now())",
                username, username + "@example.com", hash);
        return jdbcTemplate.queryForObject("select id from users where username = ?", Long.class, username);
    }

    private void deleteTestUser(String username) {
        jdbcTemplate.update("delete from login_activities where user_id in (select id from users where username = ?)", username);
        jdbcTemplate.update("delete from refresh_tokens where user_id in (select id from users where username = ?)", username);
        jdbcTemplate.update("delete from users where username = ?", username);
    }

    @Test
    void invalidRefreshTokenThrows() {
        assertThatThrownBy(() -> authService.refreshToken("not-a-valid-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void expiredRefreshTokenThrowsAndIsDeleted() {
        String username = testUsername();
        createTestUser(username, "testpass123");
        try {
            LoginRequest login = new LoginRequest();
            login.setIdentifier(username);
            login.setPassword("testpass123");
            AuthResult initial = authService.login(login, "test-agent");
            String rawRefresh = initial.getRawRefreshToken();

            String hash = HashUtil.sha256(rawRefresh);
            jdbcTemplate.update("update refresh_tokens set expires_at = now() - interval '1 day' where token_hash = ?", hash);

            assertThatThrownBy(() -> authService.refreshToken(rawRefresh))
                    .isInstanceOf(InvalidRefreshTokenException.class);

            Integer remaining = jdbcTemplate.queryForObject(
                    "select count(*) from refresh_tokens where token_hash = ?", Integer.class, hash);
            // With noRollbackFor, the expired-token delete is committed even though the method throws.
            assertThat(remaining).isEqualTo(0);
        } finally {
            deleteTestUser(username);
        }
    }

    @Test
    void concurrentRefreshProducesOneSuccessAndReuseDetection() throws Exception {
        String username = testUsername();
        createTestUser(username, "testpass123");
        try {
            LoginRequest login = new LoginRequest();
            login.setIdentifier(username);
            login.setPassword("testpass123");
            AuthResult initial = authService.login(login, "test-agent");
            String rawRefresh = initial.getRawRefreshToken();
            assertThat(rawRefresh).isNotBlank();

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            java.util.concurrent.Callable<Object> task = () -> {
                start.await();
                try {
                    return authService.refreshToken(rawRefresh);
                } catch (InvalidRefreshTokenException e) {
                    return e;
                }
            };
            try {
                Future<Object> f1 = pool.submit(task);
                Future<Object> f2 = pool.submit(task);
                start.countDown();
                Object r1 = f1.get(30, TimeUnit.SECONDS);
                Object r2 = f2.get(30, TimeUnit.SECONDS);

                int successes = 0;
                int reuseDetected = 0;
                for (Object r : new Object[]{r1, r2}) {
                    if (r instanceof InvalidRefreshTokenException) reuseDetected++;
                    else successes++;
                }
                assertThat(successes).isEqualTo(1);
                assertThat(reuseDetected).isEqualTo(1);

                Long userId = jdbcTemplate.queryForObject("select id from users where username = ?", Long.class, username);
                // Reuse detection (deleteByUserId) is now committed via noRollbackFor, so all tokens
                // (including the successfully rotated one) are deleted.
                Integer remaining = jdbcTemplate.queryForObject(
                        "select count(*) from refresh_tokens where user_id = ?", Integer.class, userId);
                assertThat(remaining).isEqualTo(0);
            } finally {
                pool.shutdownNow();
            }
        } finally {
            deleteTestUser(username);
        }
    }
}
