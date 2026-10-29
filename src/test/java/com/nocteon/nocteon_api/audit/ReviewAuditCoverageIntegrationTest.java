package com.nocteon.nocteon_api.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

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
import com.nocteon.nocteon_api.auth.enums.Provider;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.category.repository.CategoryRepository;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.review.entity.Review;
import com.nocteon.nocteon_api.review.entity.ReviewStatus;
import com.nocteon.nocteon_api.review.repository.ReviewRepository;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * The REVIEW half of the audit log: approve, un-approve, reject, delete.
 *
 * <p>Moderation is the textbook case for an audit trail — a review is a shopper's
 * public words, and taking them down (or letting them through) is a decision
 * somebody made. The rows answer "who let this through" and "who took it down".
 *
 * <p>Two details are deliberate and pinned here:
 *
 * <ul>
 *   <li>re-approving an already approved review writes NO row: nothing was decided,
 *       and a row would claim a decision.</li>
 *   <li>the row carries the product by ID in metadata, never by slug: resolving a
 *       slug would initialise a lazy relation once per review, and a bulk approve of
 *       twenty reviews must not become forty queries.</li>
 * </ul>
 */
@SpringBootTest(classes = NocteonApiApplication.class)
@AutoConfigureMockMvc
class ReviewAuditCoverageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ReviewRepository reviewRepository;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    private String suffix;
    private User admin;
    private User author;
    private Product product;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        deleteFixtures();

        admin = userRepository.save(User.builder()
                .username("auditcov-rev-admin-" + suffix)
                .email("auditcov-rev-admin-" + suffix + "@example.test")
                .password("x")
                .provider(Provider.LOCAL)
                .role("MODERATOR")
                .enabled(true)
                .build());

        author = userRepository.save(User.builder()
                .username("auditcov-rev-author-" + suffix)
                .email("auditcov-rev-author-" + suffix + "@example.test")
                .password("x")
                .provider(Provider.LOCAL)
                .role("CUSTOMER")
                .enabled(true)
                .build());

        Category category = categoryRepository.save(
                Category.builder().slug("auditcov-rev-cat-" + suffix).isActive(true).build());
        product = Product.builder()
                .slug("auditcov-rev-" + suffix)
                .category(category)
                .productType(ProductType.COFFEE)
                .isActive(true)
                .build();
        product.getTranslations().add(ProductTranslation.builder()
                .product(product).language("en").name("Audit Review Coffee " + suffix).build());
        product = productRepository.save(product);
    }

    @AfterEach
    void tearDown() {
        deleteFixtures();
    }

    // ── approve ──────────────────────────────────────────────────────────────

    @Test
    void approvingAReviewRecordsTheModeratorAndTheStatusItMovedFrom() throws Exception {
        Review review = saveReview(ReviewStatus.PENDING);

        mockMvc.perform(asAdmin(patch("/api/dashboard/reviews/{id}/approval", review.getId())
                        .param("approved", "true")))
                .andExpect(status().isOk());

        Map<String, Object> row = latestRow(AuditAction.REVIEW_APPROVE, review.getId());
        assertThat(row.get("user_id")).isEqualTo(admin.getId());
        assertThat(row.get("user_email")).isEqualTo(admin.getEmail());
        assertThat(row.get("entity_type")).isEqualTo("Review");
        assertThat(oldValues(row)).containsEntry("status", "PENDING");
        assertThat(newValues(row)).containsEntry("status", "APPROVED");
        // Lazy-relation safe: the product is referenced by id, not by slug.
        assertThat(metadata(row)).containsEntry("productId", product.getId().intValue());
    }

    @Test
    void unApprovingAReviewIsAuditedAsAnUpdate() throws Exception {
        Review review = saveReview(ReviewStatus.APPROVED);

        mockMvc.perform(asAdmin(patch("/api/dashboard/reviews/{id}/approval", review.getId())
                        .param("approved", "false")))
                .andExpect(status().isOk());

        Map<String, Object> row = latestRow(AuditAction.REVIEW_UPDATE, review.getId());
        assertThat(oldValues(row)).containsEntry("status", "APPROVED");
        assertThat(newValues(row)).containsEntry("status", "PENDING");
        // The action name matches what happened: nothing was approved here.
        assertThat(countRows(AuditAction.REVIEW_APPROVE, review.getId())).isZero();
    }

    @Test
    void approvingAnAlreadyApprovedReviewWritesNoRow() throws Exception {
        Review review = saveReview(ReviewStatus.APPROVED);

        mockMvc.perform(asAdmin(patch("/api/dashboard/reviews/{id}/approval", review.getId())
                        .param("approved", "true")))
                .andExpect(status().isOk());

        assertThat(countRows(AuditAction.REVIEW_APPROVE, review.getId())).isZero();
        assertThat(countRows(AuditAction.REVIEW_UPDATE, review.getId())).isZero();
    }

    // ── reject ───────────────────────────────────────────────────────────────

    @Test
    void rejectingAReviewRecordsTheModeratorAndTheStatusItMovedFrom() throws Exception {
        Review review = saveReview(ReviewStatus.PENDING);

        mockMvc.perform(asAdmin(patch("/api/dashboard/reviews/{id}/reject", review.getId())))
                .andExpect(status().isOk());

        Map<String, Object> row = latestRow(AuditAction.REVIEW_REJECT, review.getId());
        assertThat(row.get("user_id")).isEqualTo(admin.getId());
        assertThat(row.get("user_email")).isEqualTo(admin.getEmail());
        assertThat(oldValues(row)).containsEntry("status", "PENDING");
        assertThat(newValues(row)).containsEntry("status", "REJECTED");
    }

    // ── delete ───────────────────────────────────────────────────────────────

    @Test
    void deletingAReviewRecordsWhoRemovedIt() throws Exception {
        Review review = saveReview(ReviewStatus.APPROVED);

        mockMvc.perform(asAdmin(delete("/api/dashboard/reviews/{id}", review.getId())))
                .andExpect(status().isNoContent());

        Map<String, Object> row = latestRow(AuditAction.REVIEW_DELETE, review.getId());
        assertThat(row.get("user_id")).isEqualTo(admin.getId());
        assertThat(row.get("user_email")).isEqualTo(admin.getEmail());
        assertThat(oldValues(row)).containsEntry("status", "APPROVED");
        assertThat(String.valueOf(newValues(row).get("deletedAt"))).isNotBlank();
        assertThat(metadata(row)).containsEntry("byAuthor", false);
    }

    /**
     * The negative: a moderation call that fails writes no success row.
     *
     * <p>Approving a review that is not there answers 404 and leaves the log alone —
     * the row would otherwise say a review was approved that never existed.
     */
    @Test
    void moderatingAReviewThatDoesNotExistWritesNoRow() throws Exception {
        long missingId = 987_654_321L;

        mockMvc.perform(asAdmin(patch("/api/dashboard/reviews/{id}/approval", missingId)
                        .param("approved", "true")))
                .andExpect(status().isNotFound());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.audit_logs WHERE entity_type = 'Review' AND entity_id = ?",
                Integer.class, missingId);
        assertThat(rows).isZero();
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private Review saveReview(ReviewStatus status) {
        return reviewRepository.save(Review.builder()
                .product(product)
                .user(author)
                .rating(4)
                .comment("A fixture review " + suffix)
                .isVerified(false)
                .status(status)
                .build());
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        User fresh = userRepository.findById(admin.getId()).orElseThrow();
        UserPrincipal principal = UserPrincipal.create(fresh);
        return request.with(authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())));
    }

    private Map<String, Object> latestRow(AuditAction action, Long reviewId) {
        return jdbcTemplate.queryForMap(
                "SELECT user_id, user_email, entity_type, old_values, new_values, metadata "
                        + "FROM public.audit_logs WHERE action = ? AND entity_type = 'Review' AND entity_id = ? "
                        + "AND success = true ORDER BY id DESC LIMIT 1",
                action.name(), reviewId);
    }

    private int countRows(AuditAction action, Long reviewId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.audit_logs WHERE action = ? AND entity_type = 'Review' "
                        + "AND entity_id = ? AND success = true",
                Integer.class, action.name(), reviewId);
        return count == null ? 0 : count;
    }

    private static Map<String, Object> oldValues(Map<String, Object> row) {
        return readJson((String) row.get("old_values"));
    }

    private static Map<String, Object> newValues(Map<String, Object> row) {
        return readJson((String) row.get("new_values"));
    }

    private static Map<String, Object> metadata(Map<String, Object> row) {
        return readJson((String) row.get("metadata"));
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
        jdbcTemplate.update("DELETE FROM public.audit_logs WHERE user_email IN (?, ?)",
                "auditcov-rev-admin-" + suffix + "@example.test",
                "auditcov-rev-author-" + suffix + "@example.test");
        jdbcTemplate.update(
                "DELETE FROM public.audit_logs WHERE entity_type = 'Review' AND entity_id IN "
                        + "(SELECT id FROM reviews WHERE product_id IN "
                        + "(SELECT id FROM products WHERE slug LIKE 'auditcov-rev-%'))");
        // Reviews cascade from the product; the product carries its translation with it.
        jdbcTemplate.update("DELETE FROM products WHERE slug LIKE 'auditcov-rev-%'");
        jdbcTemplate.update("DELETE FROM categories WHERE slug LIKE 'auditcov-rev-cat-%'");
        jdbcTemplate.update("DELETE FROM users WHERE username LIKE 'auditcov-rev-%'");
    }
}
