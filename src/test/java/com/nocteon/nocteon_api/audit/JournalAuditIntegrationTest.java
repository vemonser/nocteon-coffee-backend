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
import com.nocteon.nocteon_api.journal.dto.request.JournalCategoryRequest;
import com.nocteon.nocteon_api.journal.dto.request.JournalCategoryTranslationRequest;
import com.nocteon.nocteon_api.journal.dto.request.JournalPostRequest;
import com.nocteon.nocteon_api.journal.dto.request.JournalPostTranslationRequest;
import com.nocteon.nocteon_api.journal.dto.response.JournalCategoryResponseDashboard;
import com.nocteon.nocteon_api.journal.dto.response.JournalPostResponseDashboard;
import com.nocteon.nocteon_api.journal.entity.JournalPost;
import com.nocteon.nocteon_api.journal.enums.JournalPostStatus;
import com.nocteon.nocteon_api.journal.repository.JournalPostRepository;
import com.nocteon.nocteon_api.journal.service.JournalService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * The JOURNAL half of the audit log: posts and categories.
 *
 * <p>The journal is public content, so "who published this, and when" is the
 * question the log answers. Publishing is not a separate endpoint — the editor
 * saves a post with {@code status = PUBLISHED} — so the status transition and the
 * {@code publishedAt} stamp it sets both have to show up on the UPDATE row.
 *
 * <p>Fixtures carry a random suffix and cleanup is scoped to it: this suite runs
 * against a shared dev database, so the assertions never count rows the test did
 * not create.
 */
@SpringBootTest(classes = NocteonApiApplication.class)
class JournalAuditIntegrationTest {

    private static final String POST = "JournalPost";
    private static final String CATEGORY = "JournalCategory";

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private JournalPostRepository postRepository;
    @Autowired private JournalService journalService;

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
                .username("auditcov-journal-admin-" + suffix)
                .email("auditcov-journal-admin-" + suffix + "@example.test")
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
    void categoryCreateRenameAndDeleteAreAudited() {
        JournalCategoryResponseDashboard category = journalService.createCategory(categoryRequest("Journal Category " + suffix));

        Map<String, Object> created = latestRow(AuditAction.JOURNAL_CATEGORY_CREATE, CATEGORY, category.getId());
        assertThat(created.get("user_id")).isEqualTo(admin.getId());
        assertThat(created.get("user_email")).isEqualTo(admin.getEmail());
        assertThat(created.get("entity_slug")).isEqualTo(category.getSlug());
        assertThat(newValues(created)).containsEntry("name", "Journal Category " + suffix);

        journalService.renameCategory(category.getId(), categoryRequest("Journal Category " + suffix + " Renamed"));
        Map<String, Object> renamed = latestRow(AuditAction.JOURNAL_CATEGORY_UPDATE, CATEGORY, category.getId());
        assertThat(oldValues(renamed)).containsEntry("name", "Journal Category " + suffix);
        assertThat(newValues(renamed)).containsEntry("name", "Journal Category " + suffix + " Renamed");

        journalService.deleteCategory(category.getId());
        Map<String, Object> deleted = latestRow(AuditAction.JOURNAL_CATEGORY_DELETE, CATEGORY, category.getId());
        assertThat(deleted.get("user_id")).isEqualTo(admin.getId());
        assertThat(oldValues(deleted)).containsEntry("deletedAt", null);
        assertThat(String.valueOf(newValues(deleted).get("deletedAt"))).isNotBlank();
    }

    /**
     * A draft that goes live: the UPDATE row must carry the status pair and the
     * {@code publishedAt} the service stamps, because that is the moment the
     * storefront started serving the post.
     */
    @Test
    void publishingAPostRecordsTheStatusChangeAndThePublishDate() {
        JournalCategoryResponseDashboard category = journalService.createCategory(
                categoryRequest("Journal Category " + suffix));
        String title = "Journal Post " + suffix;

        JournalPostResponseDashboard created = journalService.create(
                postRequest(category.getSlug(), title, JournalPostStatus.DRAFT), null, admin.getId());
        JournalPost post = postRepository.findBySlugForDashboard(created.getSlug()).orElseThrow();

        Map<String, Object> createRow = latestRow(AuditAction.JOURNAL_POST_CREATE, POST, post.getId());
        assertThat(createRow.get("user_id")).isEqualTo(admin.getId());
        assertThat(createRow.get("user_email")).isEqualTo(admin.getEmail());
        assertThat(createRow.get("entity_slug")).isEqualTo(post.getSlug());
        assertThat(newValues(createRow))
                .containsEntry("title", title)
                .containsEntry("status", "DRAFT");

        journalService.update(post.getSlug(),
                postRequest(category.getSlug(), title, JournalPostStatus.PUBLISHED), null);

        Map<String, Object> publishRow = latestRow(AuditAction.JOURNAL_POST_UPDATE, POST, post.getId());
        assertThat(publishRow.get("user_id")).isEqualTo(admin.getId());
        assertThat(oldValues(publishRow))
                .containsEntry("status", "DRAFT")
                .containsEntry("publishedAt", null);
        assertThat(newValues(publishRow)).containsEntry("status", "PUBLISHED");
        assertThat(String.valueOf(newValues(publishRow).get("publishedAt"))).isNotBlank();
        // The title did not move, so the row does not repeat it.
        assertThat(newValues(publishRow)).doesNotContainKey("title");

        journalService.delete(post.getSlug());
        Map<String, Object> deleteRow = latestRow(AuditAction.JOURNAL_POST_DELETE, POST, post.getId());
        assertThat(deleteRow.get("user_id")).isEqualTo(admin.getId());
        assertThat(oldValues(deleteRow)).containsEntry("deletedAt", null);
        assertThat(String.valueOf(newValues(deleteRow).get("deletedAt"))).isNotBlank();
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private static JournalCategoryRequest categoryRequest(String name) {
        JournalCategoryTranslationRequest english = new JournalCategoryTranslationRequest();
        english.setLanguage("en");
        english.setName(name);

        // The journal validates en + ar, like the rest of the dashboard.
        JournalCategoryTranslationRequest arabic = new JournalCategoryTranslationRequest();
        arabic.setLanguage("ar");
        arabic.setName("اختبار " + name.length());

        JournalCategoryRequest request = new JournalCategoryRequest();
        request.setTranslations(List.of(english, arabic));
        return request;
    }

    private static JournalPostRequest postRequest(String categorySlug, String title, JournalPostStatus status) {
        JournalPostTranslationRequest english = new JournalPostTranslationRequest();
        english.setLanguage("en");
        english.setTitle(title);
        english.setExcerpt("audit fixture");
        english.setContent("A short body for the audit fixture.");

        JournalPostTranslationRequest arabic = new JournalPostTranslationRequest();
        arabic.setLanguage("ar");
        arabic.setTitle("اختبار " + title.length());
        arabic.setContent("نص قصير.");

        JournalPostRequest request = new JournalPostRequest();
        request.setCategorySlug(categorySlug);
        request.setStatus(status);
        request.setTranslations(List.of(english, arabic));
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
        String posts = "journal-post-" + suffix + "%";
        String categories = "journal-category-" + suffix + "%";
        jdbcTemplate.update("DELETE FROM public.audit_logs WHERE user_email = ?",
                "auditcov-journal-admin-" + suffix + "@example.test");
        jdbcTemplate.update("DELETE FROM journal_post_translations WHERE journal_post_id IN "
                + "(SELECT id FROM journal_posts WHERE slug LIKE ?)", posts);
        jdbcTemplate.update("DELETE FROM journal_post_tags WHERE journal_post_id IN "
                + "(SELECT id FROM journal_posts WHERE slug LIKE ?)", posts);
        jdbcTemplate.update("DELETE FROM product_journal_posts WHERE journal_post_id IN "
                + "(SELECT id FROM journal_posts WHERE slug LIKE ?)", posts);
        jdbcTemplate.update("DELETE FROM journal_posts WHERE slug LIKE ?", posts);
        jdbcTemplate.update("DELETE FROM journal_category_translations WHERE journal_category_id IN "
                + "(SELECT id FROM journal_categories WHERE slug LIKE ?)", categories);
        jdbcTemplate.update("DELETE FROM journal_categories WHERE slug LIKE ?", categories);
        jdbcTemplate.update("DELETE FROM users WHERE username LIKE ?", "auditcov-journal-%");
    }
}
