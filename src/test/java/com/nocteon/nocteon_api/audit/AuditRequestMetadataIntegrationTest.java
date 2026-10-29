package com.nocteon.nocteon_api.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
 * WHERE an audited action came from: the client IP and the user agent of the request.
 *
 * <p>{@code public.audit_logs} has both columns, but every writer passed {@code null} for them —
 * the writing services never see the {@code HttpServletRequest} — so every row recorded {@code
 * ip_address = null, user_agent = null}. An audit page could say WHO blocked an account and never
 * FROM WHERE, which is the first question asked when an account is compromised.
 *
 * <p>The metadata is therefore read at write time from the request the thread is serving, not
 * threaded through service signatures. These tests prove both halves of that: an HTTP call records
 * the request's own values (and the resolver's proxy rule, so a spoofed header does not win), and a
 * worker thread with no request behind it still writes its row, with nulls.
 *
 * <p>{@code rate-limit.trusted-proxies=127.0.0.1} puts MockMvc's default peer in the trusted set;
 * the CIDR/first-hop rules themselves are covered by {@code ClientIpResolverTest}. Rows go to the
 * real local database, so every fixture is created and deleted here by name. No seed, no production
 * data.
 */
@SpringBootTest(classes = NocteonApiApplication.class,
        properties = "rate-limit.trusted-proxies=127.0.0.1")
@AutoConfigureMockMvc
class AuditRequestMetadataIntegrationTest {

    private static final String ADMIN_USERNAME = "auditmeta_admin";
    private static final String ADMIN_EMAIL = "auditmeta-admin@example.test";
    private static final String TARGET_USERNAME = "auditmeta_target";
    private static final String TARGET_EMAIL = "auditmeta-target@example.test";

    /** The peer the app sees: a reverse proxy we control, so its forwarded headers are honored. */
    private static final String TRUSTED_PROXY_PEER = "127.0.0.1";
    /** What the proxy says the real client is. */
    private static final String PROXY_CLIENT_IP = "203.0.113.42";
    /** A peer that is NOT a trusted proxy: its forwarded headers must be ignored. */
    private static final String UNTRUSTED_PEER = "198.51.100.7";
    /** The IP the untrusted peer claims in the header it is not allowed to set. */
    private static final String SPOOFED_HEADER_IP = "203.0.113.42";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/141.0.0.0 Safari/537.36";

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

    // ── the HTTP path ─────────────────────────────────────────────────────────

    @Test
    void anAuditedHttpActionRecordsTheClientIpAndUserAgentOfTheRequest() throws Exception {
        Long targetId = insertUser(TARGET_USERNAME, TARGET_EMAIL, "CUSTOMER", true);

        mockMvc.perform(asAdmin(patch("/api/dashboard/users/{id}/toggle-active", targetId))
                        .header("User-Agent", USER_AGENT)
                        .header("X-Real-IP", PROXY_CLIENT_IP)
                        .with(request -> {
                            request.setRemoteAddr(TRUSTED_PROXY_PEER);
                            return request;
                        }))
                .andExpect(status().isOk());

        Map<String, Object> row = latestRow(AuditAction.USER_BLOCKED, targetId);
        // The proxy's forwarded IP wins because the peer is a trusted proxy.
        assertThat(row.get("ip_address")).isEqualTo(PROXY_CLIENT_IP);
        // The header goes in verbatim: it is evidence, not a parsed summary.
        assertThat(row.get("user_agent")).isEqualTo(USER_AGENT);
        // The rest of the row is untouched by this change.
        assertThat(row.get("user_id")).isEqualTo(adminId);
        assertThat(row.get("user_email")).isEqualTo(ADMIN_EMAIL);
    }

    @Test
    void theAuditEndpointExposesTheRecordedIpAndUserAgent() throws Exception {
        Long targetId = insertUser(TARGET_USERNAME, TARGET_EMAIL, "CUSTOMER", true);

        mockMvc.perform(asAdmin(patch("/api/dashboard/users/{id}/toggle-active", targetId))
                        .header("User-Agent", USER_AGENT)
                        .header("X-Real-IP", PROXY_CLIENT_IP)
                        .with(request -> {
                            request.setRemoteAddr(TRUSTED_PROXY_PEER);
                            return request;
                        }))
                .andExpect(status().isOk());

        // The read contract already carries both fields; the drawer can render them whenever it
        // wants to, with no backend change.
        mockMvc.perform(asAdmin(get("/api/dashboard/audit-logs"))
                        .param("action", AuditAction.USER_BLOCKED.name())
                        .param("actor", ADMIN_EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].ipAddress").value(PROXY_CLIENT_IP))
                .andExpect(jsonPath("$.data.content[0].userAgent").value(USER_AGENT));
    }

    @Test
    void aForwardedHeaderFromAnUntrustedPeerDoesNotChooseTheRecordedIp() throws Exception {
        Long targetId = insertUser(TARGET_USERNAME, TARGET_EMAIL, "CUSTOMER", true);

        mockMvc.perform(asAdmin(patch("/api/dashboard/users/{id}/toggle-active", targetId))
                        .header("User-Agent", USER_AGENT)
                        .header("X-Real-IP", SPOOFED_HEADER_IP)
                        .header("X-Forwarded-For", SPOOFED_HEADER_IP)
                        .with(request -> {
                            request.setRemoteAddr(UNTRUSTED_PEER);
                            return request;
                        }))
                .andExpect(status().isOk());

        Map<String, Object> row = latestRow(AuditAction.USER_BLOCKED, targetId);
        // The socket address is the only address an untrusted caller cannot forge.
        assertThat(row.get("ip_address")).isEqualTo(UNTRUSTED_PEER);
        assertThat(row.get("ip_address")).isNotEqualTo(SPOOFED_HEADER_IP);
        assertThat(row.get("user_agent")).isEqualTo(USER_AGENT);
    }

    @Test
    void aRequestWithNoUserAgentRecordsNullInsteadOfAPlaceholder() throws Exception {
        Long targetId = insertUser(TARGET_USERNAME, TARGET_EMAIL, "CUSTOMER", true);

        // A script or a probe can send no User-Agent at all. The row then has no user agent:
        // recording "unknown" would put a string in an evidence column that no client sent.
        mockMvc.perform(asAdmin(patch("/api/dashboard/users/{id}/toggle-active", targetId))
                        .header("X-Real-IP", PROXY_CLIENT_IP)
                        .with(request -> {
                            request.setRemoteAddr(TRUSTED_PROXY_PEER);
                            return request;
                        }))
                .andExpect(status().isOk());

        Map<String, Object> row = latestRow(AuditAction.USER_BLOCKED, targetId);
        assertThat(row.get("user_agent")).isNull();
        // The IP is still recorded: the two columns are independent.
        assertThat(row.get("ip_address")).isEqualTo(PROXY_CLIENT_IP);
    }

    @Test
    void anOversizedUserAgentIsTruncatedInsteadOfFailingTheAction() throws Exception {
        Long targetId = insertUser(TARGET_USERNAME, TARGET_EMAIL, "CUSTOMER", true);
        String hugeUserAgent = "A".repeat(4096);

        mockMvc.perform(asAdmin(patch("/api/dashboard/users/{id}/toggle-active", targetId))
                        .header("User-Agent", hugeUserAgent)
                        .header("X-Real-IP", PROXY_CLIENT_IP)
                        .with(request -> {
                            request.setRemoteAddr(TRUSTED_PROXY_PEER);
                            return request;
                        }))
                .andExpect(status().isOk());

        Map<String, Object> row = latestRow(AuditAction.USER_BLOCKED, targetId);
        String stored = (String) row.get("user_agent");
        // 512: the cap documented on AuditRequestMetadataResolver.MAX_USER_AGENT_LENGTH.
        assertThat(stored).hasSize(512);
        assertThat(stored).isEqualTo(hugeUserAgent.substring(0, 512));
        assertThat(row.get("ip_address")).isEqualTo(PROXY_CLIENT_IP);
    }

    // ── the no-request path ───────────────────────────────────────────────────

    @Test
    void aWorkerThreadWithNoRequestStoresNullsAndDoesNotThrow() throws Exception {
        Long targetId = insertUser(TARGET_USERNAME, TARGET_EMAIL, "CUSTOMER", true);

        // The scheduler shape: the service is called with no principal and off any request
        // thread, so there is no IP and no user agent to record — and nothing may blow up.
        ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "audit-worker"));
        try {
            Future<?> write = worker.submit(() -> userService.toggleActive(targetId, null));
            write.get(30, TimeUnit.SECONDS);
        } finally {
            worker.shutdownNow();
        }

        Map<String, Object> row = latestRow(AuditAction.USER_BLOCKED, targetId);
        assertThat(row.get("ip_address")).isNull();
        assertThat(row.get("user_agent")).isNull();
        // The honest system row: written, with no invented actor either.
        assertThat(row.get("user_id")).isNull();
        assertThat(row.get("user_email")).isNull();
    }

    // ── the fixtures ──────────────────────────────────────────────────────────

    /** An authenticated caller whose principal is a real {@link UserPrincipal} (what production sets). */
    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        User admin = userRepository.findById(adminId).orElseThrow();
        UserPrincipal principal = UserPrincipal.create(admin);
        return request.with(authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())));
    }

    private Map<String, Object> latestRow(AuditAction action, Long entityId) {
        return jdbcTemplate.queryForMap(
                "SELECT user_id, user_email, ip_address, user_agent, success FROM public.audit_logs "
                        + "WHERE action = ? AND entity_type = 'User' AND entity_id = ? ORDER BY id DESC LIMIT 1",
                action.name(), entityId);
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
                id, "Audit", "Metadata");
        return id;
    }

    /** Removes every row this class can write, by name — the audit rows first, the users last. */
    private void deleteFixtures() {
        jdbcTemplate.update(
                "DELETE FROM public.audit_logs WHERE user_email IN (?, ?)", ADMIN_EMAIL, TARGET_EMAIL);
        jdbcTemplate.update(
                "DELETE FROM public.audit_logs WHERE entity_type = 'User' AND entity_id IN "
                        + "(SELECT id FROM users WHERE username LIKE 'auditmeta%')");
        jdbcTemplate.update(
                "DELETE FROM refresh_tokens WHERE user_id IN "
                        + "(SELECT id FROM users WHERE username LIKE 'auditmeta%')");
        jdbcTemplate.update(
                "DELETE FROM login_activities WHERE user_id IN "
                        + "(SELECT id FROM users WHERE username LIKE 'auditmeta%')");
        jdbcTemplate.update("DELETE FROM users WHERE username LIKE 'auditmeta%'");
    }
}
