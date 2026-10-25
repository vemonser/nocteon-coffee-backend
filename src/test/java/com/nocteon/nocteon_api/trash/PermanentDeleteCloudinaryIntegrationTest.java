package com.nocteon.nocteon_api.trash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.category.service.CategoryService;
import com.nocteon.nocteon_api.product.service.ProductService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

@SpringBootTest(classes = NocteonApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PermanentDeleteCloudinaryIntegrationTest {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ProductService productService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    private long createSoftDeletedCategory(String slug, String imageUrl) {
        jdbcTemplate.update(
                "insert into categories (slug, image_url, is_active, created_at, updated_at, deleted_at) " +
                        "values (?, ?, true, now(), now(), now())",
                slug, imageUrl);
        Long id = jdbcTemplate.queryForObject("select id from categories where slug = ?", Long.class, slug);
        jdbcTemplate.update("insert into category_translations (category_id, language, name) values (?, 'en', ?)", id, "Tmp " + slug);
        jdbcTemplate.update("insert into category_translations (category_id, language, name) values (?, 'ar', ?)", id, "tmp " + slug);
        return id;
    }

    private void hardDeleteCategory(long id) {
        jdbcTemplate.update("delete from category_translations where category_id = ?", id);
        jdbcTemplate.update("delete from categories where id = ?", id);
    }

    @Test
    void dbRollbackDoesNotInvokeCloudinaryDeletion() {
        // Category 1 (coffee) is referenced by products -> permanent delete fails on FK.
        long probeId = 1L;
        // ensure soft-deleted
        jdbcTemplate.update("update categories set deleted_at = now() where id = ?", probeId);
        try {
            assertThatThrownBy(() -> categoryService.permanentDelete(probeId))
                    .isInstanceOf(Exception.class);
            // row must still be present (transaction rolled back)
            Integer count = jdbcTemplate.queryForObject(
                    "select count(*) from categories where id = ? and deleted_at is not null", Integer.class, probeId);
            assertThat(count).isEqualTo(1);
        } finally {
            jdbcTemplate.update("update categories set deleted_at = null where id = ?", probeId);
        }
    }

    @Test
    void successfulCommitDeletesRowWithNonCloudinaryImage() {
        String slug = "zz-pd-noncloud-" + System.nanoTime();
        long id = createSoftDeletedCategory(slug, "https://example.com/old.png");
        try {
            categoryService.permanentDelete(id);
            Integer count = jdbcTemplate.queryForObject("select count(*) from categories where id = ?", Integer.class, id);
            assertThat(count).isEqualTo(0);
        } finally {
            hardDeleteCategory(id);
        }
    }

    @Test
    void cloudinaryFailureAfterCommitDoesNotRollbackDbDeletion() {
        String slug = "zz-pd-cloud-" + System.nanoTime();
        long id = createSoftDeletedCategory(slug, "https://res.cloudinary.com/x/image/upload/nocteon/test.png");
        try {
            // Cloudinary is unconfigured -> destroy throws ImageUploadException, but it runs
            // after commit and is swallowed; the DB row must still be gone and no exception propagate.
            categoryService.permanentDelete(id);
            Integer count = jdbcTemplate.queryForObject("select count(*) from categories where id = ?", Integer.class, id);
            assertThat(count).isEqualTo(0);
        } finally {
            hardDeleteCategory(id);
        }
    }

    @Test
    void productAttemptsAllMediaDeletionsEvenIfOneFails() {
        // Create a soft-deleted product with two Cloudinary-like media URLs.
        String slug = "zz-pd-prod-" + System.nanoTime();
        jdbcTemplate.update(
                "insert into products (slug, category_id, product_type, is_active, created_at, updated_at, deleted_at) " +
                        "values (?, 1, 'COFFEE', true, now(), now(), now())",
                slug);
        Long productId = jdbcTemplate.queryForObject("select id from products where slug = ?", Long.class, slug);
        jdbcTemplate.update("insert into product_translations (product_id, language, name) values (?, 'en', ?)", productId, "Tmp " + slug);
        jdbcTemplate.update("insert into product_translations (product_id, language, name) values (?, 'ar', ?)", productId, "tmp " + slug);
        jdbcTemplate.update("insert into product_media (product_id, url, type, sort_order) values (?, ?, 'IMAGE', 0)",
                productId, "https://res.cloudinary.com/x/image/upload/nocteon/a.png");
        jdbcTemplate.update("insert into product_media (product_id, url, type, sort_order) values (?, ?, 'IMAGE', 1)",
                productId, "https://res.cloudinary.com/x/image/upload/nocteon/b.png");
        try {
            // Both Cloudinary deletes are attempted (each fails and is swallowed); no exception propagates.
            productService.permanentDelete(productId);
            Integer count = jdbcTemplate.queryForObject("select count(*) from products where id = ?", Integer.class, productId);
            assertThat(count).isEqualTo(0);
        } finally {
            jdbcTemplate.update("delete from product_media where product_id = ?", productId);
            jdbcTemplate.update("delete from product_translations where product_id = ?", productId);
            jdbcTemplate.update("delete from products where id = ?", productId);
        }
    }
}
