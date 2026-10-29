package com.nocteon.nocteon_api.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.audit.enums.AuditAction;
import com.nocteon.nocteon_api.auth.dto.request.ChangePasswordRequest;
import com.nocteon.nocteon_api.auth.dto.request.LoginRequest;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.enums.Provider;
import com.nocteon.nocteon_api.auth.exception.AccountDisabledException;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.auth.service.AuthService;
import com.nocteon.nocteon_api.auth.service.UserService;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidCredentialsException;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * The AUTH half of the audit log: who signed in, who FAILED to sign in, who signed
 * out, who changed their password.
 *
 * <p>The failure rows are the ones support actually asks for ("the customer says
 * they cannot log in") and the ones a scheme like this usually loses, because the
 * login transaction is rolled back for most refusals. They are written in their own
 * transaction for exactly that reason, and the tests below prove the row survives
 * the rollback while no SUCCESS row claims a sign-in happened.
 *
 * <p>Service-level, like the existing password-lifecycle test: the controller's only
 * extra work is a Redis rate limit, which is not what these rows are about.
 */
@SpringBootTest(classes = NocteonApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AuthAuditCoverageIntegrationTest {

    private static final String PASSWORD = "Xk9#mP2$vL7@qR4!";

    @Autowired
    private AuthService authService;
    @Autowired
    private UserService userService;
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

    private String suffix;
    private User user;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        deleteFixtures();
        user = createUser(true, true);
    }

    @AfterEach
    void tearDown() {
        deleteFixtures();
    }

    // ── sign-in ──────────────────────────────────────────────────────────────

    @Test
    void aSuccessfulLoginRecordsTheAccountAndTheOutcome() {
        authService.login(loginRequest(user.getEmail(), PASSWORD), "audit-test-agent");

        Map<String, Object> row = latestRow(AuditAction.USER_LOGIN, user.getId(), true);
        // The actor IS the account: the password match is what proves it.
        assertThat(row.get("user_id")).isEqualTo(user.getId());
        assertThat(row.get("user_email")).isEqualTo(user.getEmail());
        assertThat(row.get("entity_type")).isEqualTo("User");
        assertThat(row.get("entity_id")).isEqualTo(user.getId());
        assertThat(newValues(row)).containsEntry("role", "CUSTOMER").containsEntry("provider", "LOCAL");
    }

    /**
     * A wrong password: the attempt is recorded, the sign-in is not.
     *
     * <p>No actor is invented — a wrong password proves nothing about who typed it —
     * and no success row exists, so the page can never read "this account signed in".
     */
    @Test
    void aFailedLoginRecordsTheAttemptWithNoSuccessRowAndNoInventedActor() {
        assertThatThrownBy(() -> authService.login(loginRequest(user.getEmail(), "Wrong-Password-1!"), "agent"))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(countSuccessRows(AuditAction.USER_LOGIN, user.getId())).isZero();

        Map<String, Object> row = latestRow(AuditAction.USER_LOGIN, user.getId(), false);
        assertThat(row.get("user_id")).isNull();
        assertThat(row.get("user_email")).isNull();
        assertThat(row.get("error_message")).isEqualTo("The password did not match.");
        assertThat(metadata(row)).containsEntry("failedAttempts", 1);
    }

    /**
     * A blocked account: the sign-in is refused, and the attempt is still recorded.
     *
     * <p>This is the case support cares most about, and the one a naive scheme loses
     * (the login transaction would roll the row back with the refusal). The refusal
     * writes nothing else, so it does not roll back, and the row commits.
     */
    @Test
    void aBlockedAccountsFailedLoginIsRecorded() {
        user.setActive(false);
        user = userRepository.save(user);

        assertThatThrownBy(() -> authService.login(loginRequest(user.getEmail(), PASSWORD), "agent"))
                .isInstanceOf(AccountDisabledException.class);

        Map<String, Object> row = latestRow(AuditAction.USER_LOGIN, user.getId(), false);
        assertThat(row.get("error_message")).isEqualTo("The account is blocked.");
        assertThat(countSuccessRows(AuditAction.USER_LOGIN, user.getId())).isZero();
    }

    @Test
    void aLoginForAnIdentifierNobodyHoldsRecordsTheAttemptButNoSubject() {
        String unknown = "auditcov-auth-unknown-" + suffix + "@example.test";

        assertThatThrownBy(() -> authService.login(loginRequest(unknown, PASSWORD), "agent"))
                .isInstanceOf(InvalidCredentialsException.class);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT user_id, user_email, entity_id, error_message, metadata FROM public.audit_logs "
                        + "WHERE action = ? AND success = false AND metadata LIKE ? ORDER BY id DESC LIMIT 1",
                AuditAction.USER_LOGIN.name(), "%" + unknown + "%");
        assertThat(row.get("user_id")).isNull();
        assertThat(row.get("entity_id")).isNull();
        assertThat(metadata(row)).containsEntry("identifier", unknown);
    }

    // ── sign-out ─────────────────────────────────────────────────────────────

    @Test
    void loggingOutRecordsWhoEndedTheSession() {
        authService.logout(null, null, UserPrincipal.create(userRepository.findById(user.getId()).orElseThrow()));

        Map<String, Object> row = latestRow(AuditAction.USER_LOGOUT, user.getId(), true);
        assertThat(row.get("user_id")).isEqualTo(user.getId());
        assertThat(row.get("user_email")).isEqualTo(user.getEmail());
    }

    // ── password ─────────────────────────────────────────────────────────────

    @Test
    void changingThePasswordRecordsTheAccountAndWhatItCost() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword(PASSWORD);
        request.setNewPassword("Zr8$nQ3&wK6!pL9@");
        request.setConfirmPassword("Zr8$nQ3&wK6!pL9@");

        userService.changePassword(
                UserPrincipal.create(userRepository.findById(user.getId()).orElseThrow()), request);

        Map<String, Object> row = latestRow(AuditAction.PASSWORD_CHANGE, user.getId(), true);
        assertThat(row.get("user_id")).isEqualTo(user.getId());
        assertThat(row.get("user_email")).isEqualTo(user.getEmail());
        assertThat(metadata(row)).containsEntry("sessionsRevoked", true);

        // The password itself never reaches the log — neither the hash nor the new value.
        String stored = String.valueOf(row.get("old_values")) + row.get("new_values") + row.get("metadata");
        assertThat(stored).doesNotContain("Zr8", "Xk9", "$2a$", "hashed");
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private User createUser(boolean enabled, boolean active) {
        String username = "auditcov-auth-" + suffix;
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.test")
                .password(passwordEncoder.encode(PASSWORD))
                .provider(Provider.LOCAL)
                .role("CUSTOMER")
                .enabled(enabled)
                .isActive(active)
                .build());
    }

    private static LoginRequest loginRequest(String identifier, String password) {
        LoginRequest request = new LoginRequest();
        request.setIdentifier(identifier);
        request.setPassword(password);
        return request;
    }

    /**
     * The newest row for this action and account.
     *
     * <p>The success row is about the actor too (the account signed in), while a
     * failure row deliberately has no actor — the two are looked up separately.
     */
    private Map<String, Object> latestRow(AuditAction action, Long entityId, boolean success) {
        return jdbcTemplate.queryForMap(
                "SELECT user_id, user_email, entity_type, entity_id, old_values, new_values, metadata, "
                        + "error_message, success FROM public.audit_logs "
                        + "WHERE action = ? AND entity_id = ? AND success = ? ORDER BY id DESC LIMIT 1",
                action.name(), entityId, success);
    }

    private int countSuccessRows(AuditAction action, Long entityId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.audit_logs WHERE action = ? AND entity_id = ? AND success = true",
                Integer.class, action.name(), entityId);
        return count == null ? 0 : count;
    }

    private static Map<String, Object> newValues(Map<String, Object> row) {
        return readJson((String) row.get("new_values"));
    }

    private static Map<String, Object> metadata(Map<String, Object> row) {
        return readJson((String) row.get("metadata"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readJson(String json) {
        if (json == null) {
            return Map.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            throw new AssertionError("the audit row's JSON does not parse: " + json, e);
        }
    }

    private void deleteFixtures() {
        String username = "auditcov-auth-" + suffix;
        String email = username + "@example.test";

        jdbcTemplate.update("DELETE FROM public.audit_logs WHERE user_email = ?", email);
        jdbcTemplate.update(
                "DELETE FROM public.audit_logs WHERE entity_type = 'User' AND entity_id IN "
                        + "(SELECT id FROM users WHERE username = ?)", username);
        // The unknown-identifier attempt has neither an actor nor an entity: the typed
        // address is the only thing that identifies it, so that is what removes it.
        jdbcTemplate.update("DELETE FROM public.audit_logs WHERE action = ? AND metadata LIKE ?",
                AuditAction.USER_LOGIN.name(), "%auditcov-auth-" + suffix + "%");
        jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id IN "
                + "(SELECT id FROM users WHERE username = ?)", username);
        jdbcTemplate.update("DELETE FROM login_activities WHERE user_id IN "
                + "(SELECT id FROM users WHERE username = ?)", username);
        jdbcTemplate.update("DELETE FROM user_profiles WHERE user_id IN "
                + "(SELECT id FROM users WHERE username = ?)", username);
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", username);
    }
}
