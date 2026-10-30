package com.nocteon.nocteon_api.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.audit.enums.AuditAction;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.enums.Provider;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.shippingZone.dto.request.ShippingZoneRequest;
import com.nocteon.nocteon_api.shippingZone.dto.response.ShippingZoneResponse;
import com.nocteon.nocteon_api.shippingZone.service.ShippingZoneService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * The SHIPPING ZONE half of the audit log: create, update and delete.
 *
 * <p>A zone is a price per city — money and coverage in one row. The test pins the
 * two things an operator asks of it: who raised the price (an UPDATE row that
 * carries the cost pair and nothing that stayed the same), and who took a city out
 * of service (the delete row's timestamp).
 *
 * <p>A zone has no slug column, so the row is identified by entity type and id.
 * Fixtures carry a random suffix and cleanup is scoped to it: this suite runs
 * against a shared dev database.
 */
@SpringBootTest(classes = NocteonApiApplication.class)
class ShippingZoneAuditIntegrationTest {

    private static final String ENTITY = "ShippingZone";

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private ShippingZoneService shippingZoneService;

    @org.springframework.test.context.DynamicPropertySource
    static void jwtKeys(org.springframework.test.context.DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    private String suffix;
    private String city;
    private User admin;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        // A city can be assigned to one live zone only, and the dev data already
        // covers the real ones: the fixture uses its own name.
        city = "Auditcov City " + suffix;
        deleteFixtures();
        admin = userRepository.save(User.builder()
                .username("auditcov-ship-admin-" + suffix)
                .email("auditcov-ship-admin-" + suffix + "@example.test")
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
        ShippingZoneResponse created = shippingZoneService.create(
                zoneRequest("Auditcov shipzone " + suffix, new BigDecimal("25.00"), Set.of(city)));

        Map<String, Object> createRow = latestRow(AuditAction.SHIPPING_ZONE_CREATE, created.getId());
        assertThat(createRow.get("user_id")).isEqualTo(admin.getId());
        assertThat(createRow.get("user_email")).isEqualTo(admin.getEmail());
        assertThat(createRow.get("entity_type")).isEqualTo(ENTITY);
        assertThat(newValues(createRow))
                .containsEntry("name", "Auditcov shipzone " + suffix)
                .containsEntry("active", true);
        assertThat(((Number) newValues(createRow).get("shippingCost")).doubleValue()).isEqualTo(25.00);

        shippingZoneService.update(created.getId(),
                zoneRequest("Auditcov shipzone " + suffix, new BigDecimal("40.00"), Set.of(city)));

        Map<String, Object> updateRow = latestRow(AuditAction.SHIPPING_ZONE_UPDATE, created.getId());
        assertThat(updateRow.get("user_id")).isEqualTo(admin.getId());
        assertThat(((Number) oldValues(updateRow).get("shippingCost")).doubleValue()).isEqualTo(25.00);
        assertThat(((Number) newValues(updateRow).get("shippingCost")).doubleValue()).isEqualTo(40.00);
        // The name and the covered city did not move, so they are not in the row.
        assertThat(newValues(updateRow)).doesNotContainKey("name").doesNotContainKey("cities");

        shippingZoneService.delete(created.getId());

        Map<String, Object> deleteRow = latestRow(AuditAction.SHIPPING_ZONE_DELETE, created.getId());
        assertThat(deleteRow.get("user_id")).isEqualTo(admin.getId());
        assertThat(oldValues(deleteRow)).containsEntry("deletedAt", null);
        assertThat(String.valueOf(newValues(deleteRow).get("deletedAt"))).isNotBlank();
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private static ShippingZoneRequest zoneRequest(String name, BigDecimal cost, Set<String> cities) {
        ShippingZoneRequest request = new ShippingZoneRequest();
        request.setName(name);
        request.setShippingCost(cost);
        request.setCities(cities);
        request.setActive(true);
        return request;
    }

    private void actingAsAdmin() {
        UserPrincipal principal = UserPrincipal.create(userRepository.findById(admin.getId()).orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Map<String, Object> latestRow(AuditAction action, Long entityId) {
        return jdbcTemplate.queryForMap(
                "SELECT user_id, user_email, entity_type, old_values, new_values "
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
        String names = "Auditcov shipzone " + suffix + "%";
        jdbcTemplate.update("DELETE FROM public.audit_logs WHERE user_email = ?",
                "auditcov-ship-admin-" + suffix + "@example.test");
        jdbcTemplate.update("DELETE FROM shipping_zone_cities WHERE shipping_zone_id IN "
                + "(SELECT id FROM shipping_zones WHERE name LIKE ?)", names);
        jdbcTemplate.update("DELETE FROM shipping_zones WHERE name LIKE ?", names);
        jdbcTemplate.update("DELETE FROM users WHERE username LIKE ?", "auditcov-ship-%");
    }
}
