package com.nocteon.nocteon_api.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.audit.enums.AuditAction;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.enums.Provider;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.product.dto.request.ProductOptionRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductTagRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductTagTranslationRequest;
import com.nocteon.nocteon_api.product.dto.request.TranslationRequest;
import com.nocteon.nocteon_api.product.dto.response.ProductOptionResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductTagResponseDashboard;
import com.nocteon.nocteon_api.product.service.ProductOptionService;
import com.nocteon.nocteon_api.product.service.ProductTagService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * The PRODUCT TAG and PRODUCT OPTION half of the audit log.
 *
 * <p>Both are small catalogues that change what a product can be: a tag is how the
 * storefront filters, an option is how a variant is built ("Size: 250g"). The test
 * pins that their create, edit and delete each leave a row with the actor, and
 * that an edit records only what moved.
 *
 * <p>An option delete is a hard delete, so the row keeps the entity's last state as
 * its {@code old_values} — there is no trashed row left to point at.
 *
 * <p>Fixtures carry a random suffix and cleanup is scoped to it: this suite runs
 * against a shared dev database.
 */
@SpringBootTest(classes = NocteonApiApplication.class)
class ProductTagOptionAuditIntegrationTest {

    private static final String TAG = "ProductTag";
    private static final String OPTION = "ProductOption";

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductTagService productTagService;
    @Autowired private ProductOptionService productOptionService;

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
                .username("auditcov-tagopt-admin-" + suffix)
                .email("auditcov-tagopt-admin-" + suffix + "@example.test")
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
    void tagCreateRenameAndDeleteAreAudited() {
        ProductTagResponseDashboard tag = productTagService.createTag(tagRequest("Tag Audit " + suffix));

        Map<String, Object> createRow = latestRow(AuditAction.PRODUCT_TAG_CREATE, TAG, tag.getId());
        assertThat(createRow.get("user_id")).isEqualTo(admin.getId());
        assertThat(createRow.get("user_email")).isEqualTo(admin.getEmail());
        assertThat(createRow.get("entity_slug")).isEqualTo(tag.getSlug());
        assertThat(newValues(createRow)).containsEntry("name", "Tag Audit " + suffix);

        productTagService.renameTag(tag.getId(), tagRequest("Tag Audit " + suffix + " Renamed"));

        Map<String, Object> renameRow = latestRow(AuditAction.PRODUCT_TAG_UPDATE, TAG, tag.getId());
        assertThat(oldValues(renameRow)).containsEntry("name", "Tag Audit " + suffix);
        assertThat(newValues(renameRow)).containsEntry("name", "Tag Audit " + suffix + " Renamed");

        productTagService.deleteTag(tag.getId());

        Map<String, Object> deleteRow = latestRow(AuditAction.PRODUCT_TAG_DELETE, TAG, tag.getId());
        assertThat(deleteRow.get("user_id")).isEqualTo(admin.getId());
        assertThat(oldValues(deleteRow)).containsEntry("deletedAt", null);
        assertThat(String.valueOf(newValues(deleteRow).get("deletedAt"))).isNotBlank();
    }

    @Test
    void optionCreateUpdateAndDeleteAreAudited() {
        ProductOptionResponse option = productOptionService.create(optionRequest("Option Audit " + suffix, 5));

        Map<String, Object> createRow = latestRow(AuditAction.PRODUCT_OPTION_CREATE, OPTION, option.getId());
        assertThat(createRow.get("user_id")).isEqualTo(admin.getId());
        assertThat(createRow.get("user_email")).isEqualTo(admin.getEmail());
        assertThat(createRow.get("entity_slug")).isEqualTo(option.getSlug());
        assertThat(newValues(createRow))
                .containsEntry("name", "Option Audit " + suffix)
                .containsEntry("sortOrder", 5);

        productOptionService.update(option.getId(), optionRequest("Option Audit " + suffix + " Renamed", 9));

        Map<String, Object> updateRow = latestRow(AuditAction.PRODUCT_OPTION_UPDATE, OPTION, option.getId());
        assertThat(oldValues(updateRow))
                .containsEntry("name", "Option Audit " + suffix)
                .containsEntry("sortOrder", 5);
        assertThat(newValues(updateRow))
                .containsEntry("name", "Option Audit " + suffix + " Renamed")
                .containsEntry("sortOrder", 9);

        productOptionService.delete(option.getId());

        // A hard delete: the row it leaves behind is the only record of the option.
        Map<String, Object> deleteRow = latestRow(AuditAction.PRODUCT_OPTION_DELETE, OPTION, option.getId());
        assertThat(deleteRow.get("user_id")).isEqualTo(admin.getId());
        assertThat(deleteRow.get("user_email")).isEqualTo(admin.getEmail());
        assertThat(deleteRow.get("entity_slug")).isEqualTo(option.getSlug());
        assertThat(oldValues(deleteRow))
                .containsEntry("slug", option.getSlug())
                .containsEntry("sortOrder", 9);
        assertThat(newValues(deleteRow)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM product_options WHERE id = ?", Integer.class, option.getId())).isZero();
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private static ProductTagRequest tagRequest(String name) {
        ProductTagTranslationRequest english = new ProductTagTranslationRequest();
        english.setLanguage("en");
        english.setName(name);

        ProductTagTranslationRequest arabic = new ProductTagTranslationRequest();
        arabic.setLanguage("ar");
        arabic.setName("اختبار " + name.length());

        ProductTagRequest request = new ProductTagRequest();
        request.setTranslations(List.of(english, arabic));
        return request;
    }

    private static ProductOptionRequest optionRequest(String name, int sortOrder) {
        TranslationRequest english = new TranslationRequest();
        english.setLanguage("en");
        english.setName(name);

        TranslationRequest arabic = new TranslationRequest();
        arabic.setLanguage("ar");
        arabic.setName("اختبار " + name.length());

        ProductOptionRequest request = new ProductOptionRequest();
        request.setTranslations(List.of(english, arabic));
        request.setSortOrder(sortOrder);
        return request;
    }

    private void actingAsAdmin() {
        UserPrincipal principal = UserPrincipal.create(userRepository.findById(admin.getId()).orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Map<String, Object> latestRow(AuditAction action, String entityType, Long entityId) {
        return jdbcTemplate.queryForMap(
                "SELECT user_id, user_email, entity_type, entity_slug, old_values, new_values "
                        + "FROM public.audit_logs WHERE action = ? AND entity_type = ? AND entity_id = ? "
                        + "AND success = true ORDER BY id DESC LIMIT 1",
                action.name(), entityType, entityId);
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
                "auditcov-tagopt-admin-" + suffix + "@example.test");
        jdbcTemplate.update("DELETE FROM product_tag_translations WHERE tag_id IN "
                + "(SELECT id FROM product_tags WHERE slug LIKE ?)", "tag-audit-" + suffix + "%");
        jdbcTemplate.update("DELETE FROM product_tags WHERE slug LIKE ?", "tag-audit-" + suffix + "%");
        jdbcTemplate.update("DELETE FROM product_option_translations WHERE option_id IN "
                + "(SELECT id FROM product_options WHERE slug LIKE ?)", "option-audit-" + suffix + "%");
        jdbcTemplate.update("DELETE FROM product_option_values WHERE option_id IN "
                + "(SELECT id FROM product_options WHERE slug LIKE ?)", "option-audit-" + suffix + "%");
        jdbcTemplate.update("DELETE FROM product_options WHERE slug LIKE ?", "option-audit-" + suffix + "%");
        jdbcTemplate.update("DELETE FROM users WHERE username LIKE ?", "auditcov-tagopt-%");
    }
}
