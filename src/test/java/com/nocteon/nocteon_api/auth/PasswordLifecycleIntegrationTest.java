package com.nocteon.nocteon_api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.auth.dto.request.ChangePasswordRequest;
import com.nocteon.nocteon_api.auth.dto.request.LoginRequest;
import com.nocteon.nocteon_api.auth.dto.request.RegisterRequest;
import com.nocteon.nocteon_api.auth.dto.request.ResetPasswordRequest;
import com.nocteon.nocteon_api.auth.dto.request.VerifyEmailRequest;
import com.nocteon.nocteon_api.auth.dto.response.AuthResult;
import com.nocteon.nocteon_api.auth.dto.response.RegisterResponse;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.auth.service.AuthService;
import com.nocteon.nocteon_api.auth.service.EmailVerificationService;
import com.nocteon.nocteon_api.auth.service.PasswordResetService;
import com.nocteon.nocteon_api.auth.service.UserService;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidCredentialsException;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidRefreshTokenException;
import com.nocteon.nocteon_api.support.TestJwtKeys;

@SpringBootTest(classes = NocteonApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PasswordLifecycleIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private UserService userService;
    @Autowired private PasswordResetService passwordResetService;
    @Autowired private EmailVerificationService emailVerificationService;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    private String username() {
        return "pwtest_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private long createUser(String username, String password, boolean enabled) {
        jdbcTemplate.update("""
                insert into users (username, email, password, enabled, provider, role, is_active, created_at, updated_at)
                values (?, ?, ?, ?, 'LOCAL', 'CUSTOMER', true, now(), now())
                """, username, username + "@example.com", passwordEncoder.encode(password), enabled);
        return jdbcTemplate.queryForObject("select id from users where username = ?", Long.class, username);
    }

    private void deleteUser(String username) {
        jdbcTemplate.update("delete from user_profiles where user_id in (select id from users where username = ?)", username);
        jdbcTemplate.update("delete from login_activities where user_id in (select id from users where username = ?)", username);
        jdbcTemplate.update("delete from refresh_tokens where user_id in (select id from users where username = ?)", username);
        jdbcTemplate.update("delete from verification_codes where user_id in (select id from users where username = ?)", username);
        jdbcTemplate.update("delete from users where username = ?", username);
    }

    private AuthResult login(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setIdentifier(username);
        request.setPassword(password);
        return authService.login(request, "test-agent");
    }

    @Test
    void registrationNormalizesEmailAndCreatesAnUnverifiedLocalUser() {
        String username = username();
        String rawEmail = username.toUpperCase() + "@Example.COM";
        String expectedEmail = username + "@example.com";
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Test");
        request.setLastName("User");
        request.setUsername(username);
        request.setPhone("01012345678");
        request.setEmail(rawEmail);
        request.setPassword("Xk9#mP2$vL7@qR4!");
        try {
            RegisterResponse response = authService.register(request);

            assertThat(response.getEmail()).isEqualTo(expectedEmail);
            User user = userRepository.findByEmail(expectedEmail).orElseThrow();
            assertThat(user.isEnabled()).isFalse();
            assertThat(user.getProvider()).isEqualTo(com.nocteon.nocteon_api.auth.enums.Provider.LOCAL);
        } finally {
            deleteUser(username);
        }
    }

    @Test
    void changePasswordInvalidatesRefreshTokensAndAccessVersion() {
        String username = username();
        String oldPassword = "Xk9#mP2$vL7@qR4!";
        String newPassword = "Zr8$nQ3&wK6!pL9@";
        createUser(username, oldPassword, true);
        try {
            AuthResult initial = login(username, oldPassword);
            long userId = jdbcTemplate.queryForObject(
                    "select id from users where username = ?", Long.class, username);
            int beforeVersion = jdbcTemplate.queryForObject(
                    "select token_version from users where id = ?", Integer.class, userId);

            User user = userRepository.findById(userId).orElseThrow();
            ChangePasswordRequest change = new ChangePasswordRequest();
            change.setOldPassword(oldPassword);
            change.setNewPassword(newPassword);
            change.setConfirmPassword(newPassword);
            userService.changePassword(UserPrincipal.create(user), change);

            assertThat(jdbcTemplate.queryForObject(
                    "select token_version from users where id = ?", Integer.class, userId))
                    .isGreaterThan(beforeVersion);
            assertThatThrownBy(() -> authService.refreshToken(initial.getRawRefreshToken()))
                    .isInstanceOf(InvalidRefreshTokenException.class);
            assertThatThrownBy(() -> login(username, oldPassword))
                    .isInstanceOf(InvalidCredentialsException.class);
            assertThat(login(username, newPassword)).isNotNull();
        } finally {
            deleteUser(username);
        }
    }

    @Test
    void resetPasswordInvalidatesRefreshTokensAndAccessVersion() {
        String username = username();
        String oldPassword = "Xk9#mP2$vL7@qR4!";
        String newPassword = "Zr8$nQ3&wK6!pL9@";
        long userId = createUser(username, oldPassword, true);
        try {
            AuthResult initial = login(username, oldPassword);
            int beforeVersion = jdbcTemplate.queryForObject(
                    "select token_version from users where id = ?", Integer.class, userId);
            jdbcTemplate.update("""
                    insert into verification_codes (user_id, code_hashed, type, expires_at, used, created_at, updated_at)
                    values (?, ?, 'PASSWORD_RESET', ?, false, now(), now())
                    """, userId, passwordEncoder.encode("123456"),
                    Timestamp.from(Instant.now().plusSeconds(600)));

            ResetPasswordRequest reset = new ResetPasswordRequest();
            reset.setEmail(username + "@example.com");
            reset.setOtp("123456");
            reset.setNewPassword(newPassword);
            reset.setConfirmPassword(newPassword);
            passwordResetService.resetPassword(reset);

            assertThat(jdbcTemplate.queryForObject(
                    "select token_version from users where id = ?", Integer.class, userId))
                    .isGreaterThan(beforeVersion);
            assertThatThrownBy(() -> authService.refreshToken(initial.getRawRefreshToken()))
                    .isInstanceOf(InvalidRefreshTokenException.class);
            assertThat(login(username, newPassword)).isNotNull();
        } finally {
            deleteUser(username);
        }
    }

    @Test
    void verifyEmailEnablesTheUserAndConsumesTheCode() {
        String username = username();
        long userId = createUser(username, "Xk9#mP2$vL7@qR4!", false);
        try {
            jdbcTemplate.update("""
                    insert into verification_codes (user_id, code_hashed, type, expires_at, used, created_at, updated_at)
                    values (?, ?, 'EMAIL_VERIFICATION', ?, false, now(), now())
                    """, userId, passwordEncoder.encode("654321"),
                    Timestamp.from(Instant.now().plusSeconds(600)));

            VerifyEmailRequest request = new VerifyEmailRequest();
            request.setEmail(username + "@example.com");
            request.setOtp("654321");
            emailVerificationService.verifyEmail(request);

            assertThat(jdbcTemplate.queryForObject(
                    "select enabled from users where id = ?", Boolean.class, userId)).isTrue();
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from verification_codes where user_id = ?", Integer.class, userId)).isZero();
        } finally {
            deleteUser(username);
        }
    }

    @Test
    void resendVerificationDoesNotRevealUnknownEmail() {
        emailVerificationService.resendVerificationEmail("nobody-" + UUID.randomUUID() + "@example.com");
    }

    @Test
    void emailLookupsAreCaseInsensitive() {
        String username = username();
        createUser(username, "Xk9#mP2$vL7@qR4!", true);
        try {
            String email = username + "@example.com";
            assertThat(userRepository.findByEmail(email.toUpperCase())).isPresent();
            assertThat(userRepository.existsByEmail(email.toUpperCase())).isTrue();
        } finally {
            deleteUser(username);
        }
    }
}
