package com.nocteon.nocteon_api.trash;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.category.service.CategoryService;
import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.journal.service.JournalService;
import com.nocteon.nocteon_api.product.dto.response.DashboardProductResponse;
import com.nocteon.nocteon_api.product.service.ProductService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * Integration tests for the trash-bin flows.
 *
 * <p>These exercise the real native queries against the development database, which is
 * the only way to catch the kind of defects the unit suite cannot see: a ClassCastException
 * on a native-query row cast, a non-deterministic row order, or a missing restore path.
 *
 * <p>Requires PostgreSQL + Redis up ({@code docker compose up -d}) and a schema migrated
 * through the latest Flyway version. Mutates rows 5/6/7 of {@code products} and a seeded
 * journal post, and restores everything in {@code finally} blocks.
 */
@SpringBootTest(
        classes = NocteonApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TrashBinIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private JournalService journalService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Test
    void productTrashFlow() {
        // product 7 (aeropress) is used as the probe row
        long probeId = 7L;
        String probeSlug = "aeropress";
        try {
            productService.delete(probeSlug);

            PageResponse<DashboardProductResponse> trashed =
                    productService.getAllDeleted(new LookupFilterRequest());
            assertThat(trashed.getContent())
                    .extracting(DashboardProductResponse::getSlug)
                    .contains(probeSlug);

            productService.restore(probeId);

            PageResponse<DashboardProductResponse> after =
                    productService.getAllDeleted(new LookupFilterRequest());
            assertThat(after.getContent())
                    .extracting(DashboardProductResponse::getSlug)
                    .doesNotContain(probeSlug);
        } finally {
            // guarantee the row is live again even if an assertion failed
            jdbcTemplate.update("update products set deleted_at = null where id = ?", probeId);
        }
    }

    @Test
    void categoryTrashFlow() {
        // category 3 (gift-cards) is used as the probe row
        long probeId = 3L;
        String probeSlug = "gift-cards";
        try {
            // soft-delete via the repository-less path: set deleted_at directly
            jdbcTemplate.update("update categories set deleted_at = now() where id = ?", probeId);

            PageResponse<?> trashed = categoryService.getAllDeleted(new LookupFilterRequest());
            assertThat(trashed.getContent())
                    .extracting("slug")
                    .contains(probeSlug);

            categoryService.restore(probeId);

            PageResponse<?> after = categoryService.getAllDeleted(new LookupFilterRequest());
            assertThat(after.getContent())
                    .extracting("slug")
                    .doesNotContain(probeSlug);
        } finally {
            jdbcTemplate.update("update categories set deleted_at = null where id = ?", probeId);
        }
    }

    @Test
    void journalPostTrashFlowExercisesEnumCast() {
        long probeId = 9999L;
        try {
            jdbcTemplate.update(
                    "insert into journal_posts (id, journal_category_id, author_id, slug, status, "
                            + "created_at, updated_at, deleted_at) values (?, 1, 1, ?, 'PUBLISHED', now(), now(), now()) "
                            + "on conflict (id) do nothing",
                    probeId, "zz-inttest-journal-post");
            jdbcTemplate.update(
                    "insert into journal_post_translations (journal_post_id, language, title, content) "
                            + "values (?, 'en', 'ZZ IntTest', 'content') on conflict do nothing",
                    probeId);

            PageResponse<?> trashed = journalService.getAllDeleted(new LookupFilterRequest());
            List<?> content = trashed.getContent();
            Object found = content.stream()
                    .filter(r -> "zz-inttest-journal-post".equals(invokeSlug(r)))
                    .findFirst()
                    .orElse(null);

            assertThat(found).as("the seeded PUBLISHED post must appear in the trash listing").isNotNull();
            // the enum cast (JournalPostStatus.valueOf) only succeeds on a real JDBC row
            assertThat(invokeStatus(found)).isEqualTo("PUBLISHED");
        } finally {
            jdbcTemplate.update("delete from journal_post_translations where journal_post_id = ?", probeId);
            jdbcTemplate.update("delete from journal_posts where id = ?", probeId);
        }
    }

    private static String invokeSlug(Object r) {
        try {
            return (String) r.getClass().getMethod("getSlug").invoke(r);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String invokeStatus(Object r) {
        try {
            Object s = r.getClass().getMethod("getStatus").invoke(r);
            return s == null ? null : s.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
