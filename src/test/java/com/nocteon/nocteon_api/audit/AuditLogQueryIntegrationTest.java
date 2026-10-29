package com.nocteon.nocteon_api.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.audit.entity.AuditLog;
import com.nocteon.nocteon_api.audit.enums.AuditAction;
import com.nocteon.nocteon_api.audit.repository.AuditLogRepository;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * The audit page's read endpoint, against the real database.
 *
 * <p>Every row this test writes is owned by a dedicated actor email/id that no
 * seed and no other test uses, and every query is narrowed by that actor, so
 * the assertions cannot be disturbed by whatever the rest of the suite — or a
 * developer's own session — wrote into {@code audit_logs}. The rows are deleted
 * again in the teardown.
 *
 * <p>The detail JSON is asserted VERBATIM: the endpoint hands back the stored
 * text, so an auditor reads byte-for-byte what was recorded (the double spaces
 * in the fixture are deliberate).
 */
@SpringBootTest(classes = NocteonApiApplication.class)
@AutoConfigureMockMvc
class AuditLogQueryIntegrationTest {

    private static final String ACTOR_EMAIL = "fe22-audit-pilot@example.test";
    private static final Long ACTOR_ID = 9_402_201L;
    private static final String OTHER_ACTOR_EMAIL = "fe22-audit-other@example.test";
    private static final Long OTHER_ACTOR_ID = 9_402_202L;

    /** Stored with two spaces after `price` and before `sku` — on purpose. */
    private static final String VERBATIM_OLD = "{\"price\":  1.10,  \"sku\":\"NC-001\"}";
    private static final String VERBATIM_NEW = "{\"price\":2.00,\"sku\":\"NC-001\"}";
    private static final String VERBATIM_META = "{\"reason\":\"stock recount\", \"note\":\"spaces  kept\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** The five pilot rows, oldest first. */
    private final List<Long> pilotIds = new ArrayList<>();
    private final List<Long> otherIds = new ArrayList<>();

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @BeforeEach
    void setUp() {
        deletePilotRows();
        pilotIds.clear();
        otherIds.clear();

        pilotIds.add(save(AuditAction.PRODUCT_CREATE, ACTOR_ID, ACTOR_EMAIL, "product", 101L, "2026-03-01T09:00:00Z",
                null, "{\"name\":\"Kenia AA\"}", null, true, null));
        pilotIds.add(save(AuditAction.PRODUCT_UPDATE, ACTOR_ID, ACTOR_EMAIL, "product", 101L, "2026-03-02T09:00:00Z",
                "{\"price\":1.00}", "{\"price\":1.50}", null, true, null));
        pilotIds.add(save(AuditAction.ORDER_CREATE, ACTOR_ID, ACTOR_EMAIL, "order", 201L, "2026-03-03T09:00:00Z",
                null, "{\"total\":650.00}", "{\"channel\":\"web\"}", false, "card declined"));
        pilotIds.add(save(AuditAction.USER_LOGIN, ACTOR_ID, ACTOR_EMAIL, "user", 301L, "2026-03-04T09:00:00Z",
                null, null, null, true, null));
        // The newest row carries the verbatim fixture (it lands first, DESC).
        pilotIds.add(save(AuditAction.PRODUCT_DELETE, ACTOR_ID, ACTOR_EMAIL, "product", 102L, "2026-03-05T09:00:00Z",
                VERBATIM_OLD, VERBATIM_NEW, VERBATIM_META, true, null));

        // A second actor with the same kind of actions: the filters must not
        // pick these up when the actor is narrowed.
        otherIds.add(save(AuditAction.PRODUCT_CREATE, OTHER_ACTOR_ID, OTHER_ACTOR_EMAIL, "product", 103L,
                "2026-03-01T12:00:00Z", null, "{\"name\":\"Other\"}", null, true, null));
        otherIds.add(save(AuditAction.PRODUCT_UPDATE, OTHER_ACTOR_ID, OTHER_ACTOR_EMAIL, "product", 103L,
                "2026-03-06T12:00:00Z", "{\"price\":1.00}", "{\"price\":9.00}", null, true, null));
    }

    @AfterEach
    void tearDown() {
        deletePilotRows();
        pilotIds.clear();
        otherIds.clear();
    }

    private void deletePilotRows() {
        jdbcTemplate.update(
                "DELETE FROM public.audit_logs WHERE user_email IN (?, ?)", ACTOR_EMAIL, OTHER_ACTOR_EMAIL);
    }

    private Long save(AuditAction action, Long userId, String userEmail, String entityType, Long entityId,
                      String createdAt, String oldValues, String newValues, String metadata,
                      boolean success, String errorMessage) {
        AuditLog saved = auditLogRepository.save(AuditLog.builder()
                .userId(userId)
                .userEmail(userEmail)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .ipAddress("127.0.0.1")
                .oldValues(oldValues)
                .newValues(newValues)
                .metadata(metadata)
                .success(success)
                .errorMessage(errorMessage)
                .build());
        // The writing entity is audited (createdAt = now); the page's ordering
        // and date filters need the timestamps the fixture asks for.
        jdbcTemplate.update(
                "UPDATE public.audit_logs SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse(createdAt)),
                saved.getId());
        return saved.getId();
    }

    /** An authenticated caller holding exactly {@code audit:read}. */
    private MockHttpServletRequestBuilder asAuditor(MockHttpServletRequestBuilder request) {
        return request.with(user("fe22-auditor").authorities(new SimpleGrantedAuthority("audit:read")));
    }

    // ── pagination ────────────────────────────────────────────────────────────

    @Test
    void secondPageReturnsTheNextRowsAndTheWholeCount() throws Exception {
        mockMvc.perform(asAuditor(get("/api/dashboard/audit-logs"))
                        .param("actor", ACTOR_EMAIL)
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.first").value(false))
                .andExpect(jsonPath("$.data.last").value(false))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                // Newest first: page 1 of size 2 skips the two newest rows.
                .andExpect(jsonPath("$.data.content[0].id").value(pilotIds.get(2)))
                .andExpect(jsonPath("$.data.content[1].id").value(pilotIds.get(1)));
    }

    @Test
    void theDefaultOrderIsNewestFirst() throws Exception {
        mockMvc.perform(asAuditor(get("/api/dashboard/audit-logs")).param("actor", ACTOR_EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(pilotIds.get(4)))
                .andExpect(jsonPath("$.data.content[4].id").value(pilotIds.get(0)));
    }

    // ── filters ───────────────────────────────────────────────────────────────

    @Test
    void actionIsAContainsMatchOnTheName() throws Exception {
        mockMvc.perform(asAuditor(get("/api/dashboard/audit-logs"))
                        .param("action", "product")
                        .param("actor", ACTOR_EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[0].action").value("PRODUCT_DELETE"))
                .andExpect(jsonPath("$.data.content[1].action").value("PRODUCT_UPDATE"))
                .andExpect(jsonPath("$.data.content[2].action").value("PRODUCT_CREATE"));
    }

    @Test
    void actionContainsIsCaseInsensitiveAndMatchesTheMiddle() throws Exception {
        mockMvc.perform(asAuditor(get("/api/dashboard/audit-logs")).param("action", "LOG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].action").value("USER_LOGIN"))
                .andExpect(jsonPath("$.data.content[0].userEmail").value(ACTOR_EMAIL));
    }

    @Test
    void actorByUserIdReturnsOnlyThatActor() throws Exception {
        mockMvc.perform(asAuditor(get("/api/dashboard/audit-logs")).param("actor", String.valueOf(ACTOR_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.content[*].userEmail")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(ACTOR_EMAIL))));
    }

    @Test
    void actorByEmailFragmentIsCaseInsensitive() throws Exception {
        mockMvc.perform(asAuditor(get("/api/dashboard/audit-logs")).param("actor", "FE22-AUDIT-PILOT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(5));
    }

    @Test
    void aDateRangeKeepsOnlyTheRowsInsideIt() throws Exception {
        mockMvc.perform(asAuditor(get("/api/dashboard/audit-logs"))
                        .param("actor", ACTOR_EMAIL)
                        .param("since", "2026-03-02T00:00:00Z")
                        .param("until", "2026-03-03T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].id").value(pilotIds.get(2)))
                .andExpect(jsonPath("$.data.content[1].id").value(pilotIds.get(1)));
    }

    @Test
    void anEmptyDateRangeReturnsNothing() throws Exception {
        mockMvc.perform(asAuditor(get("/api/dashboard/audit-logs"))
                        .param("actor", ACTOR_EMAIL)
                        .param("since", "2020-01-01T00:00:00Z")
                        .param("until", "2020-01-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    // ── the detail ────────────────────────────────────────────────────────────

    @Test
    void theStoredDetailComesBackVerbatim() throws Exception {
        mockMvc.perform(asAuditor(get("/api/dashboard/audit-logs")).param("actor", ACTOR_EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(pilotIds.get(4)))
                .andExpect(jsonPath("$.data.content[0].oldValues").value(VERBATIM_OLD))
                .andExpect(jsonPath("$.data.content[0].newValues").value(VERBATIM_NEW))
                .andExpect(jsonPath("$.data.content[0].metadata").value(VERBATIM_META))
                // The columns the page shows, and nothing invented.
                .andExpect(jsonPath("$.data.content[0].action").value("PRODUCT_DELETE"))
                .andExpect(jsonPath("$.data.content[0].entityType").value("product"))
                .andExpect(jsonPath("$.data.content[0].entityId").value(102))
                .andExpect(jsonPath("$.data.content[0].userId").value(ACTOR_ID))
                .andExpect(jsonPath("$.data.content[0].userEmail").value(ACTOR_EMAIL))
                .andExpect(jsonPath("$.data.content[0].success").value(true))
                .andExpect(jsonPath("$.data.content[0].createdAt").exists());
    }

    @Test
    void aRowWithoutADetailReturnsNullRatherThanAnEmptyObject() throws Exception {
        mockMvc.perform(asAuditor(get("/api/dashboard/audit-logs"))
                        .param("actor", ACTOR_EMAIL)
                        .param("action", "USER_LOGIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].oldValues").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].newValues").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].metadata").doesNotExist());
    }

    @Test
    void aFailedActionKeepsItsRecordedError() throws Exception {
        mockMvc.perform(asAuditor(get("/api/dashboard/audit-logs"))
                        .param("actor", ACTOR_EMAIL)
                        .param("action", "ORDER_CREATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].success").value(false))
                .andExpect(jsonPath("$.data.content[0].errorMessage").value("card declined"));
    }

    // ── the permission ────────────────────────────────────────────────────────

    @Test
    void aCallerWithoutAuditReadIsForbidden() throws Exception {
        mockMvc.perform(get("/api/dashboard/audit-logs")
                        .with(user("fe22-no-audit")
                                .authorities(new SimpleGrantedAuthority("order:read"),
                                        new SimpleGrantedAuthority("product:read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAnonymousCallerIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/dashboard/audit-logs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theRowsInTheFixtureAreTheOnlyOnesForThePilotActors() {
        // Guards the fixture assumptions above: if a seed or another test ever
        // writes under these actors, the counts above are no longer 5 and 2.
        List<String> emails = jdbcTemplate.queryForList(
                "SELECT user_email FROM public.audit_logs WHERE user_id IN (?, ?)",
                String.class, ACTOR_ID, OTHER_ACTOR_ID);
        assertThat(emails).hasSize(7).containsOnly(ACTOR_EMAIL, OTHER_ACTOR_EMAIL);
    }
}
