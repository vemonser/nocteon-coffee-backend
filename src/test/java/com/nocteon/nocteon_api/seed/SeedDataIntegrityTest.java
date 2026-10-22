package com.nocteon.nocteon_api.seed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * The seed data a moderator sees.
 *
 * <p>Three defects made the reviews page look broken: the primary image of
 * Brazil Mogiana was a concert photograph, three of the fifteen reviews were
 * written by the admin account — so the moderation queue showed a moderator
 * reviewing their own store's products — and every review claimed a confirmed
 * purchase, including the ones whose buyer never bought the product.
 */
@SpringBootTest(
        classes = NocteonApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SeedDataIntegrityTest {

    /** The concert photograph that was Brazil Mogiana's primary image. */
    private static final String CONCERT_PHOTO = "photo-1516450360452-9312f5e86fc7";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Test
    void noReviewIsWrittenByAnAdmin() {
        Integer adminReviews = jdbcTemplate.queryForObject(
                "select count(*) from reviews r join users u on u.id = r.user_id "
                        + "where u.role = 'ADMIN'",
                Integer.class);

        assertThat(adminReviews).isZero();
    }

    @Test
    void theBrazilMogianaPrimaryImageIsNotTheConcert() {
        String url = jdbcTemplate.queryForObject(
                "select m.url from product_media m join products p on p.id = m.product_id "
                        + "where p.slug = 'brazil-mogiana' and m.is_primary",
                String.class);

        assertThat(url).isNotNull();
        assertThat(url).doesNotContain(CONCERT_PHOTO);
        assertThat(url).contains("unsplash.com");
    }

    @Test
    void theConcertPhotographIsGoneFromEveryProduct() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from product_media where url like ?",
                Integer.class, "%" + CONCERT_PHOTO + "%");

        assertThat(count).isZero();
    }

    @Test
    void theReviewCountsAreUnchanged() {
        Integer total = jdbcTemplate.queryForObject(
                "select count(*) from reviews where deleted_at is null", Integer.class);
        Integer approved = jdbcTemplate.queryForObject(
                "select count(*) from reviews where deleted_at is null and status = 'APPROVED'",
                Integer.class);
        Integer pending = jdbcTemplate.queryForObject(
                "select count(*) from reviews where deleted_at is null and status = 'PENDING'",
                Integer.class);
        Integer rejected = jdbcTemplate.queryForObject(
                "select count(*) from reviews where deleted_at is null and status = 'REJECTED'",
                Integer.class);

        // The SEED is the truth: 17 reviews, 15 of them approved and 2 still
        // waiting for a moderator.
        assertThat(total).isEqualTo(17);
        assertThat(approved).isEqualTo(15);
        assertThat(pending).isEqualTo(2);
        assertThat(rejected).isZero();
    }

    /**
     * A verified review points at an order. The dashboard shows a check mark
     * next to the reviewer's name and links it to that order, so a review
     * that claims a confirmed purchase without one is a broken promise.
     */
    @Test
    void everyVerifiedReviewHasAQualifyingOrder() {
        Integer orphans = jdbcTemplate.queryForObject(
                "select count(*) from reviews r where r.is_verified = true and not exists ("
                        + "select 1 from orders o join order_items oi on oi.order_id = o.id "
                        + "join product_variants pv on pv.id = oi.product_variant_id "
                        + "where o.user_id = r.user_id and pv.product_id = r.product_id "
                        + "and o.status not in ('CANCELLED', 'PENDING'))",
                Integer.class);

        assertThat(orphans).isZero();
    }
}
