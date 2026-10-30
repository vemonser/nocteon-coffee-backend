package com.nocteon.nocteon_api.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.audit.enums.AuditAction;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.enums.Provider;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.promoCode.dto.request.PromoCodeRequest;
import com.nocteon.nocteon_api.promoCode.dto.response.PromoCodeResponse;
import com.nocteon.nocteon_api.promoCode.entity.PromoCode;
import com.nocteon.nocteon_api.promoCode.enums.PromoCodeDiscountType;
import com.nocteon.nocteon_api.promoCode.enums.PromoScopeType;
import com.nocteon.nocteon_api.promoCode.repository.PromoCodeRepository;
import com.nocteon.nocteon_api.promoCode.service.PromoCodeService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * The PROMO CODE half of the audit log: create, update and delete.
 *
 * <p>A promo code is money: someone can hand out 100% off the whole catalogue. An
 * operator has to be able to answer "who made this, who raised the discount, who
 * killed it". The tests pin the three properties the audit page relies on:
 *
 * <ul>
 *   <li>every write leaves a row naming the acting admin (id AND e-mail) and the
 *       entity (type, id and the code as its slug);</li>
 *   <li>an UPDATE row carries only the fields that differ — turning a code off
 *       shows {@code active}, not a second copy of the whole form;</li>
 *   <li>a rolled-back create leaves no row, because the audit write joins the
 *       caller's transaction.</li>
 * </ul>
 *
 * <p>No {@code @Transactional} on the class: the audit writer must commit with the
 * change, and the rollback test needs a real transaction of its own. Fixtures carry
 * a random suffix and cleanup is scoped to it — this suite runs against a shared
 * dev database, so no assertion here counts rows it did not create.
 */
@SpringBootTest(classes = NocteonApiApplication.class)
class PromoCodeAuditIntegrationTest {

    private static final String ENTITY = "PromoCode";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PromoCodeRepository promoCodeRepository;
    @Autowired
    private PromoCodeService promoCodeService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @org.springframework.test.context.DynamicPropertySource
    static void jwtKeys(org.springframework.test.context.DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    private String suffix;
    private User admin;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        deleteFixtures();
        admin = userRepository.save(User.builder()
                .username("auditcov-promo-admin-" + suffix)
                .email("auditcov-promo-admin-" + suffix + "@example.test")
                .password("x")
                .provider(Provider.LOCAL)
                .role("ADMIN")
                .enabled(true)
                .build());
        actingAsAdmin();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        deleteFixtures();
    }

    @Test
    void createUpdateAndDeleteEachLeaveARowNamingTheAdmin() {
        PromoCode promoCode = create("PROMO-" + suffix);

        Map<String, Object> created = latestRow(AuditAction.PROMO_CODE_CREATE, promoCode.getId());
        assertThat(created.get("user_id")).isEqualTo(admin.getId());
        assertThat(created.get("user_email")).isEqualTo(admin.getEmail());
        assertThat(created.get("entity_type")).isEqualTo(ENTITY);
        assertThat(created.get("entity_slug")).isEqualTo(promoCode.getCode());
        assertThat(newValues(created))
                .containsEntry("code", promoCode.getCode())
                .containsEntry("active", true);
        assertThat(((Number) newValues(created).get("discountValue")).intValue()).isEqualTo(10);
        assertThat(oldValues(created)).isEmpty();

        // ── update: only the fields that moved ──────────────────────────────
        promoCodeService.update(promoCode.getId(), request(promoCode.getCode(), new BigDecimal("25"), false));

        Map<String, Object> updated = latestRow(AuditAction.PROMO_CODE_UPDATE, promoCode.getId());
        assertThat(updated.get("user_id")).isEqualTo(admin.getId());
        assertThat(updated.get("user_email")).isEqualTo(admin.getEmail());
        assertThat(oldValues(updated))
                .containsEntry("active", true)
                .doesNotContainKey("code");
        assertThat(newValues(updated))
                .containsEntry("active", false)
                .doesNotContainKey("code");
        assertThat(((Number) oldValues(updated).get("discountValue")).intValue()).isEqualTo(10);
        assertThat(((Number) newValues(updated).get("discountValue")).intValue()).isEqualTo(25);

        // ── delete: the timestamp that moved, and nothing else ──────────────
        promoCodeService.delete(promoCode.getId());

        Map<String, Object> deleted = latestRow(AuditAction.PROMO_CODE_DELETE, promoCode.getId());
        assertThat(deleted.get("user_id")).isEqualTo(admin.getId());
        assertThat(deleted.get("user_email")).isEqualTo(admin.getEmail());
        assertThat(oldValues(deleted)).containsEntry("deletedAt", null);
        assertThat(String.valueOf(newValues(deleted).get("deletedAt"))).isNotBlank();
    }

    /**
     * The audit row is written inside the change's transaction, so a create that
     * is rolled back afterwards leaves neither the code nor a row claiming it.
     *
     * <p>Scoped to the id the rolled-back create returned: this database is shared,
     * so "the table is empty" would be both wrong and flaky.
     */
    @Test
    void aRolledBackCreateLeavesNoRow() {
        String code = "PROMORB-" + suffix;
        Long[] createdId = new Long[1];

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            PromoCodeResponse response = promoCodeService.create(request(code, new BigDecimal("10"), true));
            createdId[0] = response.getId();
            assertThat(response.getId()).isNotNull();
            status.setRollbackOnly();
        });

        assertThat(createdId[0]).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.audit_logs WHERE entity_type = ? AND entity_id = ?",
                Integer.class, ENTITY, createdId[0])).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM promo_codes WHERE code = ?", Integer.class, code)).isZero();
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private PromoCode create(String code) {
        promoCodeService.create(request(code, new BigDecimal("10"), true));
        return promoCodeRepository.findByCodeIgnoreCase(code).orElseThrow();
    }

    private PromoCodeRequest request(String code, BigDecimal discountValue, boolean active) {
        PromoCodeRequest request = new PromoCodeRequest();
        request.setCode(code);
        request.setDiscountType(PromoCodeDiscountType.PERCENTAGE);
        request.setDiscountValue(discountValue);
        request.setScopeType(PromoScopeType.GLOBAL);
        request.setValidFrom(Instant.parse("2026-01-01T00:00:00Z"));
        request.setValidUntil(Instant.parse("2026-12-31T23:59:59Z"));
        request.setActive(active);
        return request;
    }

    private void actingAsAdmin() {
        UserPrincipal principal = UserPrincipal.create(userRepository.findById(admin.getId()).orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Map<String, Object> latestRow(AuditAction action, Long entityId) {
        return jdbcTemplate.queryForMap(
                "SELECT user_id, user_email, entity_type, entity_slug, old_values, new_values "
                        + "FROM public.audit_logs WHERE action = ? AND entity_type = ? AND entity_id = ? "
                        + "AND success = true ORDER BY id DESC LIMIT 1",
                action.name(), ENTITY, entityId);
    }

    private static Map<String, Object> oldValues(Map<String, Object> row) {
        return readJson((String) row.get("old_values"));
    }

    private static Map<String, Object> newValues(Map<String, Object> row) {
        return readJson((String) row.get("new_values"));
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
        jdbcTemplate.update("DELETE FROM public.audit_logs WHERE user_email = ?",
                "auditcov-promo-admin-" + suffix + "@example.test");
        String codes = "PROMO%" + suffix;
        jdbcTemplate.update(
                "DELETE FROM promo_code_categories WHERE promo_code_id IN "
                        + "(SELECT id FROM promo_codes WHERE code LIKE ?)", codes);
        jdbcTemplate.update("DELETE FROM promo_codes WHERE code LIKE ?", codes);
        jdbcTemplate.update("DELETE FROM users WHERE username LIKE ?", "auditcov-promo-%");
    }
}
