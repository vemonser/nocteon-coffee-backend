package com.nocteon.nocteon_api.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
import com.nocteon.nocteon_api.product.dto.request.ProductRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductTranslationRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductVariantRequest;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;
import com.nocteon.nocteon_api.product.service.ProductService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * The PRODUCT half of the audit log: create, update, delete, duplicate and the
 * manual stock correction.
 *
 * <p>A product row is money and catalogue in one: an operator has to be able to
 * answer "who deleted this product", "who raised this price", "who took twelve
 * units off the shelf and why". The tests pin three properties:
 *
 * <ul>
 *   <li>every one of those actions leaves a row with the acting admin (id AND
 *       e-mail) and the entity;</li>
 *   <li>an UPDATE row carries only the fields that actually changed — a price
 *       change shows the price, not the category that stayed the same;</li>
 *   <li>the stock row and the inventory movement describe the SAME change, so
 *       the two logs can never disagree about a number.</li>
 * </ul>
 *
 * <p>No {@code @Transactional} on the class: the audit writer commits with the
 * change, and the delete/stock tests drive the real routes. The update drives the
 * service directly — the multipart form needs option values the audit row does not
 * care about — with the principal set exactly the way the JWT filter sets it, which
 * is the mechanism {@code ActorResolver} reads.
 */
@SpringBootTest(classes = NocteonApiApplication.class)
@AutoConfigureMockMvc
class ProductAuditCoverageIntegrationTest {

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
    private ProductVariantRepository variantRepository;
    @Autowired
    private ProductService productService;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    private String suffix;
    private User admin;
    private Category category;
    private Product product;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        deleteFixtures();

        admin = userRepository.save(User.builder()
                .username("auditcov-prod-admin-" + suffix)
                .email("auditcov-prod-admin-" + suffix + "@example.test")
                .password("x")
                .provider(Provider.LOCAL)
                .role("ADMIN")
                .enabled(true)
                .build());

        category = categoryRepository.save(
                Category.builder().slug("auditcov-prod-cat-" + suffix).isActive(true).build());

        product = Product.builder()
                .slug("auditcov-prod-" + suffix)
                .category(category)
                .productType(ProductType.COFFEE)
                .isActive(true)
                .build();
        product.getTranslations().add(ProductTranslation.builder()
                .product(product).language("en").name("Audit Coffee " + suffix).build());
        product.getTranslations().add(ProductTranslation.builder()
                .product(product).language("ar").name("قهوة " + suffix).build());
        product = productRepository.save(product);

        variant = variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku("AUDITCOV-" + suffix)
                .price(new BigDecimal("650.00"))
                .stockQuantity(10)
                .reorderThreshold(5)
                .isActive(true)
                .version(0L)
                .build());
    }

    @AfterEach
    void tearDown() {
        deleteFixtures();
    }

    // ── delete ───────────────────────────────────────────────────────────────

    @Test
    void deletingAProductRecordsWhoTrashedItAndWhen() throws Exception {
        mockMvc.perform(asAdmin(delete("/api/dashboard/products/{slug}", product.getSlug())))
                .andExpect(status().isOk());

        Map<String, Object> row = latestRow(AuditAction.PRODUCT_DELETE, product.getId());
        assertThat(row.get("user_id")).isEqualTo(admin.getId());
        assertThat(row.get("user_email")).isEqualTo(admin.getEmail());
        assertThat(row.get("entity_type")).isEqualTo("Product");
        assertThat(row.get("entity_slug")).isEqualTo(product.getSlug());
        assertThat(oldValues(row)).containsEntry("deletedAt", null);
        assertThat(String.valueOf(newValues(row).get("deletedAt"))).isNotBlank();
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    void updatingAProductRecordsOnlyTheFieldsThatChanged() {
        ProductRequest request = updateRequest("Renamed Coffee " + suffix, new BigDecimal("700.00"));

        actingAsAdmin(() -> productService.update(product.getSlug(), request, List.of()));

        Map<String, Object> row = latestRow(AuditAction.PRODUCT_UPDATE, product.getId());
        assertThat(row.get("user_id")).isEqualTo(admin.getId());
        assertThat(row.get("user_email")).isEqualTo(admin.getEmail());

        // The name and the price moved; the category did not, so it is not in the row.
        assertThat(oldValues(row)).containsEntry("name", "Audit Coffee " + suffix);
        assertThat(newValues(row)).containsEntry("name", "Renamed Coffee " + suffix);
        assertThat(oldValues(row)).doesNotContainKey("categorySlug");
        assertThat(newValues(row)).doesNotContainKey("categorySlug");

        // The price change lives under the variant's SKU — a price is money. old_values
        // and new_values mirror each other, so the drawer shows the pair side by side.
        Map<String, Object> oldVariants = asMap(oldValues(row).get("variants"));
        Map<String, Object> newVariants = asMap(newValues(row).get("variants"));
        assertThat(asMap(oldVariants.get(variant.getSku()))).containsEntry("price", "650");
        assertThat(asMap(newVariants.get(variant.getSku()))).containsEntry("price", "700");
    }

    // ── duplicate ────────────────────────────────────────────────────────────

    @Test
    void duplicatingAProductRecordsItAsACreateWithItsSource() throws Exception {
        mockMvc.perform(asAdmin(post("/api/dashboard/products/{slug}/duplicate", product.getSlug())))
                .andExpect(status().isCreated());

        String copySlug = product.getSlug() + "-copy";
        Long copyId = jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE slug = ?", Long.class, copySlug);

        Map<String, Object> row = latestRowForEntity(AuditAction.PRODUCT_CREATE, copyId);
        assertThat(row.get("user_id")).isEqualTo(admin.getId());
        assertThat(row.get("user_email")).isEqualTo(admin.getEmail());
        assertThat(newValues(row)).containsEntry("name", "Audit Coffee " + suffix);
        assertThat(metadata(row)).containsEntry("duplicatedFrom", product.getSlug());
    }

    // ── stock ────────────────────────────────────────────────────────────────

    /**
     * The manual correction, FE-13.
     *
     * <p>Two records of one change, on purpose: the inventory movement (the
     * variant's ledger) and the audit row (who did it on purpose). They must
     * agree on the numbers and the reason, which is what this test checks.
     */
    @Test
    void aManualStockAdjustmentIsAuditedWithTheSameNumbersAsTheMovement() throws Exception {
        mockMvc.perform(asAdmin(post("/api/dashboard/products/variants/{id}/stock-adjustment", variant.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":5,\"reason\":\"Stock count correction\"}")))
                .andExpect(status().isOk());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT user_id, user_email, entity_id, entity_slug, old_values, new_values, metadata "
                        + "FROM public.audit_logs WHERE action = ? AND entity_type = 'ProductVariant' "
                        + "AND entity_id = ? ORDER BY id DESC LIMIT 1",
                AuditAction.PRODUCT_STOCK_ADJUST.name(), variant.getId());

        assertThat(row.get("user_id")).isEqualTo(admin.getId());
        assertThat(row.get("user_email")).isEqualTo(admin.getEmail());
        assertThat(row.get("entity_slug")).isEqualTo(variant.getSku());
        assertThat(oldValues(row)).containsEntry("stockQuantity", 10);
        assertThat(newValues(row)).containsEntry("stockQuantity", 15).containsEntry("delta", 5);
        assertThat(metadata(row)).containsEntry("reason", "Stock count correction");

        // The movement says exactly the same thing, and names the same admin.
        Map<String, Object> movement = jdbcTemplate.queryForMap(
                "SELECT stock_before, stock_after, delta, reason, actor FROM inventory_movements "
                        + "WHERE variant_id = ? ORDER BY id DESC LIMIT 1",
                variant.getId());
        assertThat(movement.get("stock_before")).isEqualTo(10);
        assertThat(movement.get("stock_after")).isEqualTo(15);
        assertThat(movement.get("delta")).isEqualTo(5);
        assertThat(movement.get("reason")).isEqualTo("MANUAL_ADJUSTMENT");
        assertThat(movement.get("actor")).isEqualTo(admin.getEmail());
    }

    @Test
    void aStockAdjustmentWithoutAReasonIsRefusedAndWritesNothing() throws Exception {
        mockMvc.perform(asAdmin(post("/api/dashboard/products/variants/{id}/stock-adjustment", variant.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":5,\"reason\":\"  \"}")))
                .andExpect(status().isBadRequest());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.audit_logs WHERE action = ? AND entity_id = ?",
                Integer.class, AuditAction.PRODUCT_STOCK_ADJUST.name(), variant.getId());
        assertThat(rows).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM product_variants WHERE id = ?", Integer.class, variant.getId()))
                .isEqualTo(10);
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    /** The edit form's payload: the same product, a new name and a new price. */
    private ProductRequest updateRequest(String englishName, BigDecimal price) {
        ProductTranslationRequest english = new ProductTranslationRequest();
        english.setLanguage("en");
        english.setName(englishName);

        ProductTranslationRequest arabic = new ProductTranslationRequest();
        arabic.setLanguage("ar");
        arabic.setName("قهوة " + suffix);

        ProductVariantRequest variantRequest = new ProductVariantRequest();
        variantRequest.setSku(variant.getSku());
        variantRequest.setPrice(price);
        variantRequest.setStockQuantity(10);
        variantRequest.setIsActive(true);
        variantRequest.setOptionValueIds(List.of());

        ProductRequest request = new ProductRequest();
        request.setCategorySlug(category.getSlug());
        request.setProductType(ProductType.COFFEE);
        request.setActive(true);
        request.setTranslations(List.of(english, arabic));
        request.setVariants(List.of(variantRequest));
        return request;
    }

    /** Runs the body with the principal the JWT filter would have installed. */
    private <T> T actingAsAdmin(Supplier<T> body) {
        User fresh = userRepository.findById(admin.getId()).orElseThrow();
        UserPrincipal principal = UserPrincipal.create(fresh);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            return body.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        User fresh = userRepository.findById(admin.getId()).orElseThrow();
        UserPrincipal principal = UserPrincipal.create(fresh);
        return request.with(authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())));
    }

    private Map<String, Object> latestRow(AuditAction action, Long entityId) {
        return latestRowForEntity(action, entityId);
    }

    private Map<String, Object> latestRowForEntity(AuditAction action, Long entityId) {
        return jdbcTemplate.queryForMap(
                "SELECT user_id, user_email, entity_type, entity_slug, old_values, new_values, metadata "
                        + "FROM public.audit_logs WHERE action = ? AND entity_type = 'Product' AND entity_id = ? "
                        + "AND success = true ORDER BY id DESC LIMIT 1",
                action.name(), entityId);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
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
        jdbcTemplate.update("DELETE FROM public.audit_logs WHERE user_email = ?",
                "auditcov-prod-admin-" + suffix + "@example.test");
        jdbcTemplate.update(
                "DELETE FROM public.audit_logs WHERE entity_type = 'Product' AND entity_id IN "
                        + "(SELECT id FROM products WHERE slug LIKE 'auditcov-prod-%')");
        jdbcTemplate.update(
                "DELETE FROM public.audit_logs WHERE entity_type = 'ProductVariant' AND entity_id IN "
                        + "(SELECT id FROM product_variants WHERE sku LIKE 'AUDITCOV-%')");
        jdbcTemplate.update(
                "DELETE FROM inventory_movements WHERE variant_id IN "
                        + "(SELECT id FROM product_variants WHERE sku LIKE 'AUDITCOV-%')");
        // Products cascade to their translations, variants and media.
        jdbcTemplate.update("DELETE FROM products WHERE slug LIKE 'auditcov-prod-%'");
        jdbcTemplate.update("DELETE FROM categories WHERE slug LIKE 'auditcov-prod-cat-%'");
        jdbcTemplate.update("DELETE FROM users WHERE username LIKE 'auditcov-prod-%'");
    }
}
