package com.nocteon.nocteon_api.auth;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.service.AuthService;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidCredentialsException;
import com.nocteon.nocteon_api.support.TestJwtKeys;

@SpringBootTest(classes = NocteonApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LoginAttemptLockIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    private String testUsername() {
        return "lattempt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private void createTestUser(String username) {
        String hash = passwordEncoder.encode("testpass123");
        jdbcTemplate.update(
                "insert into users (username, email, password, enabled, provider, role, is_active, " +
                        "failed_login_attempts, created_at, updated_at) " +
                        "values (?, ?, ?, true, 'LOCAL', 'CUSTOMER', true, 0, now(), now())",
                username, username + "@example.com", hash);
    }

    private void deleteTestUser(String username) {
        jdbcTemplate.update("delete from login_activities where user_id in (select id from users where username = ?)", username);
        jdbcTemplate.update("delete from refresh_tokens where user_id in (select id from users where username = ?)", username);
        jdbcTemplate.update("delete from users where username = ?", username);
    }

    @Test
    void concurrentFailedLoginsDoNotLoseIncrements() throws Exception {
        String username = testUsername();
        createTestUser(username);
        try {
            int attempts = 5;
            ExecutorService pool = Executors.newFixedThreadPool(attempts);
            CountDownLatch start = new CountDownLatch(1);
            java.util.concurrent.Callable<Object> task = () -> {
                start.await();
                LoginRequest req = new LoginRequest();
                req.setIdentifier(username);
                req.setPassword("wrongpass");
                try {
                    authService.login(req, "test-agent");
                    return "success";
                } catch (InvalidCredentialsException e) {
                    return "fail";
                }
            };
            try {
                Future<?>[] futures = new Future<?>[attempts];
                for (int i = 0; i < attempts; i++) {
                    futures[i] = pool.submit(task);
                }
                start.countDown();
                for (Future<?> f : futures) {
                    f.get(30, TimeUnit.SECONDS);
                }
            } finally {
                pool.shutdownNow();
            }

            User user = userRepository.findByUsername(username).orElseThrow();
            assertThat(user.getFailedLoginAttempts()).isEqualTo(attempts);
            assertThat(user.isLocked()).isTrue();
        } finally {
            deleteTestUser(username);
        }
    }

    @Test
    void successfulLoginResetsFailedCounter() {
        String username = testUsername();
        createTestUser(username);
        try {
            // 3 failed attempts
            for (int i = 0; i < 3; i++) {
                LoginRequest req = new LoginRequest();
                req.setIdentifier(username);
                req.setPassword("wrongpass");
                try {
                    authService.login(req, "test-agent");
                } catch (InvalidCredentialsException ignored) {
                }
            }
            User before = userRepository.findByUsername(username).orElseThrow();
            assertThat(before.getFailedLoginAttempts()).isEqualTo(3);

            // successful login resets
            LoginRequest ok = new LoginRequest();
            ok.setIdentifier(username);
            ok.setPassword("testpass123");
            authService.login(ok, "test-agent");

            User after = userRepository.findByUsername(username).orElseThrow();
            assertThat(after.getFailedLoginAttempts()).isEqualTo(0);
            assertThat(after.isLocked()).isFalse();
        } finally {
            deleteTestUser(username);
        }
    }
}
