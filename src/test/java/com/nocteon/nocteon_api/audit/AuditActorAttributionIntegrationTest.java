package com.nocteon.nocteon_api.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.audit.enums.AuditAction;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.auth.service.UserService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * WHO acted, on every audited user/role action.
 *
 * <p>The audit page exists to answer "who blocked this user / changed this role", but the
 * rows were written with a null actor ({@code user_id = null, user_email = ''}), so the
 * page rendered "System" for actions an administrator performed. This test drives each
 * audited route as a real {@link UserPrincipal} and reads the committed row back: both the
 * acting admin's id and e-mail must be there, and they must be the ADMIN's, not the target
 * user's.
 *
 * <p>The negative is asserted too: a call with no principal (the scheduler/internal shape)
 * and an anonymous HTTP call must not produce a fabricated actor.
 *
 * <p>Rows are written to the real local database (the audit writer is transactional and
 * commits with the request), so every fixture is created for this test and deleted again,
 * by name, in the teardown — and once before each test, so a crashed run cannot poison the
 * next one. No seed and no production data is touched.
 */
@SpringBootTest(classes = NocteonApiApplication.class)
@AutoConfigureMockMvc
class AuditActorAttributionIntegrationTest {

    private static final String ADMIN_USERNAME = "auditactor_admin";
    private static final String ADMIN_EMAIL = "auditactor-admin@example.test";

    private static final String TARGET_USERNAME = "auditactor_target";
    private static final String TARGET_EMAIL = "auditactor-target@example.test";

    private static final String BLOCKED_USERNAME = "auditactor_blocked";
    private static final String BLOCKED_EMAIL = "auditactor-blocked@example.test";

    /** The three role names this test can write; deleted by name in the teardown. */
    private static final String ROLE_CREATED = "AUDIT_ACTOR_CREATED";
    private static final String ROLE_UPDATED = "AUDIT_ACTOR_UPDATED";
    private static final String ROLE_DELETED = "AUDIT_ACTOR_DELETED";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    private Long adminId;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @BeforeEach
    void setUp() {
        deleteFixtures();
        adminId = insertUser(ADMIN_USERNAME, ADMIN_EMAIL, "ADMIN", true);
    }

    @AfterEach
    void tearDown() {
        deleteFixtures();
    }

    // ── user actions ──────────────────────────────────────────────────────────

    @Test
    void blockingAUserRecordsTheActingAdmin() throws Exception {
        Long targetId = insertUser(TARGET_USERNAME, TARGET_EMAIL, "CUSTOMER", true);

        mockMvc.perform(asAdmin(patch("/api/dashboard/users/{id}/toggle-active", targetId)))
                .andExpect(status().isOk());

        Map<String, Object> row = latestUserRow(AuditAction.USER_BLOCKED, targetId);
        assertThat(row.get("user_id")).isEqualTo(adminId);
        assertThat(row.get("user_email")).isEqualTo(ADMIN_EMAIL);
        assertThat(row.get("success")).isEqualTo(true);
        // The row is about the target, the actor is the admin: two different people.
        assertThat(row.get("entity_id")).isEqualTo(targetId);
        assertThat(row.get("entity_id")).isNotEqualTo(adminId);
    }

    @Test
    void unblockingAUserRecordsTheActingAdmin() throws Exception {
        Long targetId = insertUser(TARGET_USERNAME, TARGET_EMAIL, "CUSTOMER", false);

        mockMvc.perform(asAdmin(patch("/api/dashboard/users/{id}/toggle-active", targetId)))
                .andExpect(status().isOk());

        Map<String, Object> row = latestUserRow(AuditAction.USER_UNBLOCKED, targetId);
        assertThat(row.get("user_id")).isEqualTo(adminId);
        assertThat(row.get("user_email")).isEqualTo(ADMIN_EMAIL);
    }

    @Test
    void changingAUsersRoleRecordsTheActingAdmin() throws Exception {
        Long targetId = insertUser(TARGET_USERNAME, TARGET_EMAIL, "CUSTOMER", true);

        mockMvc.perform(asAdmin(put("/api/dashboard/users/{id}/role", targetId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MODERATOR\"}"))
                .andExpect(status().isOk());

        Map<String, Object> row = latestUserRow(AuditAction.ROLE_CHANGED, targetId);
        assertThat(row.get("user_id")).isEqualTo(adminId);
        assertThat(row.get("user_email")).isEqualTo(ADMIN_EMAIL);
    }

    // ── role actions ──────────────────────────────────────────────────────────

    @Test
    void creatingARoleRecordsTheActingAdmin() throws Exception {
        mockMvc.perform(asAdmin(post("/api/dashboard/roles"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + ROLE_CREATED + "\",\"description\":\"audit actor fixture\"}"))
                .andExpect(status().isCreated());

        Map<String, Object> row = latestRoleRow(AuditAction.ROLE_CREATED, ROLE_CREATED);
        assertThat(row.get("user_id")).isEqualTo(adminId);
        assertThat(row.get("user_email")).isEqualTo(ADMIN_EMAIL);
    }

    @Test
    void updatingARolesPermissionsRecordsTheActingAdmin() throws Exception {
        insertRole(ROLE_UPDATED);

        mockMvc.perform(asAdmin(put("/api/dashboard/roles/{name}", ROLE_UPDATED))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"user:read\"]}"))
                .andExpect(status().isOk());

        Map<String, Object> row = latestRoleRow(AuditAction.ROLE_PERMISSIONS_UPDATED, ROLE_UPDATED);
        assertThat(row.get("user_id")).isEqualTo(adminId);
        assertThat(row.get("user_email")).isEqualTo(ADMIN_EMAIL);
    }

    @Test
    void deletingARoleRecordsTheActingAdmin() throws Exception {
        insertRole(ROLE_DELETED);

        mockMvc.perform(asAdmin(delete("/api/dashboard/roles/{name}", ROLE_DELETED)))
                .andExpect(status().isOk());

        Map<String, Object> row = latestRoleRow(AuditAction.ROLE_DELETED, ROLE_DELETED);
        assertThat(row.get("user_id")).isEqualTo(adminId);
        assertThat(row.get("user_email")).isEqualTo(ADMIN_EMAIL);
    }

    // ── the page's read contract ──────────────────────────────────────────────

    @Test
    void theAuditEndpointReturnsTheAdminsIdAndEmailSoThePageCanNameTheActor() throws Exception {
        Long targetId = insertUser(TARGET_USERNAME, TARGET_EMAIL, "CUSTOMER", true);

        mockMvc.perform(asAdmin(patch("/api/dashboard/users/{id}/toggle-active", targetId)))
                .andExpect(status().isOk());

        // Exactly what /dashboard/settings/audit reads: the actor filter by e-mail, the
        // action filter, and the two response fields the Actor cell uses.
        mockMvc.perform(asAdmin(get("/api/dashboard/audit-logs"))
                        .param("action", "USER_BLOCKED")
                        .param("actor", ADMIN_EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].action").value("USER_BLOCKED"))
                .andExpect(jsonPath("$.data.content[0].entityId").value(targetId))
                .andExpect(jsonPath("$.data.content[0].userId").value(adminId))
                .andExpect(jsonPath("$.data.content[0].userEmail").value(ADMIN_EMAIL));
    }

    // ── the negative: no principal, no invented actor ─────────────────────────

    @Test
    void aCallWithNoPrincipalRecordsNoActorAtAll() {
        Long targetId = insertUser(TARGET_USERNAME, TARGET_EMAIL, "CUSTOMER", true);

        // The internal/scheduler shape: the service is called without a principal.
        userService.toggleActive(targetId, null);

        Map<String, Object> row = latestUserRow(AuditAction.USER_BLOCKED, targetId);
        assertThat(row.get("user_id")).isNull();
        assertThat(row.get("user_email")).isNull();
    }

    @Test
    void anAnonymousRequestIsRejectedAndWritesNoRowAtAll() throws Exception {
        Long targetId = insertUser(TARGET_USERNAME, TARGET_EMAIL, "CUSTOMER", true);

        mockMvc.perform(patch("/api/dashboard/users/{id}/toggle-active", targetId))
                .andExpect(status().isUnauthorized());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.audit_logs WHERE entity_type = 'User' AND entity_id = ?",
                Integer.class, targetId);
        assertThat(rows).isZero();
    }

    // ── the fixtures ──────────────────────────────────────────────────────────

    /** An authenticated caller whose principal is a real {@link UserPrincipal} (what production sets). */
    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        User admin = userRepository.findById(adminId).orElseThrow();
        UserPrincipal principal = UserPrincipal.create(admin);
        return request.with(authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())));
    }

    private Map<String, Object> latestUserRow(AuditAction action, Long entityId) {
        return jdbcTemplate.queryForMap(
                "SELECT user_id, user_email, entity_type, entity_id, success FROM public.audit_logs "
                        + "WHERE action = ? AND entity_type = 'User' AND entity_id = ? ORDER BY id DESC LIMIT 1",
                action.name(), entityId);
    }

    private Map<String, Object> latestRoleRow(AuditAction action, String roleName) {
        return jdbcTemplate.queryForMap(
                "SELECT user_id, user_email, entity_type, entity_slug, success FROM public.audit_logs "
                        + "WHERE action = ? AND entity_type = 'Role' AND entity_slug = ? ORDER BY id DESC LIMIT 1",
                action.name(), roleName);
    }

    private Long insertUser(String username, String email, String role, boolean active) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO users (username, email, password, enabled, provider, role, is_active, "
                        + "failed_login_attempts, created_at, updated_at) "
                        + "VALUES (?, ?, NULL, true, 'LOCAL', ?, ?, 0, now(), now()) RETURNING id",
                Long.class, username, email, role, active);
        jdbcTemplate.update(
                "INSERT INTO user_profiles (user_id, first_name, last_name, created_at, updated_at) "
                        + "VALUES (?, ?, ?, now(), now())",
                id, "Audit", "Fixture");
        return id;
    }

    private void insertRole(String name) {
        jdbcTemplate.update("INSERT INTO roles (name, description, is_system) VALUES (?, ?, false)",
                name, "audit actor fixture");
    }

    /** Removes every row this class can write, by name — the audit rows first, the users last. */
    private void deleteFixtures() {
        jdbcTemplate.update(
                "DELETE FROM public.audit_logs WHERE user_email IN (?, ?)", ADMIN_EMAIL, TARGET_EMAIL);
        jdbcTemplate.update(
                "DELETE FROM public.audit_logs WHERE entity_type = 'User' AND entity_id IN "
                        + "(SELECT id FROM users WHERE username LIKE 'auditactor%')");
        jdbcTemplate.update(
                "DELETE FROM public.audit_logs WHERE entity_type = 'Role' AND entity_slug IN (?, ?, ?)",
                ROLE_CREATED, ROLE_UPDATED, ROLE_DELETED);
        jdbcTemplate.update(
                "DELETE FROM refresh_tokens WHERE user_id IN "
                        + "(SELECT id FROM users WHERE username LIKE 'auditactor%')");
        jdbcTemplate.update(
                "DELETE FROM login_activities WHERE user_id IN "
                        + "(SELECT id FROM users WHERE username LIKE 'auditactor%')");
        jdbcTemplate.update("DELETE FROM roles WHERE name IN (?, ?, ?)",
                ROLE_CREATED, ROLE_UPDATED, ROLE_DELETED);
        jdbcTemplate.update("DELETE FROM users WHERE username LIKE 'auditactor%'");
    }
}
