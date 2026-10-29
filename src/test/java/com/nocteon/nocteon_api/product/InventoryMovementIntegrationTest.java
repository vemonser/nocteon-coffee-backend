package com.nocteon.nocteon_api.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.enums.Provider;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.category.repository.CategoryRepository;
import com.nocteon.nocteon_api.common.exception.notFound.ProductVariantNotFoundException;
import com.nocteon.nocteon_api.common.exception.product.InvalidStockAdjustmentException;
import com.nocteon.nocteon_api.common.exception.product.StockAdjustmentReasonRequiredException;
import com.nocteon.nocteon_api.product.dto.request.ProductVariantRequest;
import com.nocteon.nocteon_api.product.entity.InventoryMovement;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.enums.InventoryMovementReason;
import com.nocteon.nocteon_api.product.enums.InventoryMovementSource;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.product.repository.InventoryMovementRepository;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;
import com.nocteon.nocteon_api.product.service.InventoryMovementService;
import com.nocteon.nocteon_api.product.service.ProductVariantService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * FE-13: the inventory movement log, against the real database.
 *
 * <p>What a mock cannot prove, and this does:
 *
 * <ul>
 *   <li>each stock path writes a row with the numbers it ACTUALLY saw — the
 *       before/after pair, the delta and who did it — not the numbers the test
 *       told it to print;</li>
 *   <li>a manual adjustment without a reason is refused and writes nothing;</li>
 *   <li>the read endpoint answers newest-first and is permission-gated;</li>
 *   <li>a change that is rolled back leaves no phantom row — the movement shares
 *       the mutation's transaction.</li>
 * </ul>
 */
@SpringBootTest(classes = NocteonApiApplication.class)
@AutoConfigureMockMvc
@Transactional
class InventoryMovementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductVariantRepository variantRepository;
    @Autowired
    private InventoryMovementRepository movementRepository;
    @Autowired
    private InventoryMovementService movements;
    @Autowired
    private ProductVariantService variantService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    private Product product;
    private ProductVariant variant;
    private User admin;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category category = categoryRepository.save(
                Category.builder().slug("movement-cat-" + suffix).isActive(true).build());
        product = Product.builder()
                .slug("movement-product-" + suffix)
                .category(category)
                .productType(ProductType.COFFEE)
                .isActive(true)
                .build();
        product.getTranslations().add(ProductTranslation.builder()
                .product(product).language("en").name("Movement " + suffix).build());
        productRepository.save(product);

        variant = variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku("MOV-" + suffix)
                .price(new BigDecimal("650.00"))
                .stockQuantity(10)
                .reorderThreshold(5)
                .isActive(true)
                .version(0L)
                .build());
        entityManager.flush();

        admin = userRepository.save(User.builder()
                .username("movement-admin-" + suffix)
                .email("movement-admin-" + suffix + "@example.test")
                .password("x")
                .provider(Provider.LOCAL)
                .role("ADMIN")
                .enabled(true)
                .build());
    }

    // ─── the paths ───────────────────────────────────────────────────────────

    /** An order taking units off the shelf: 10 → 7, with the order as the source. */
    @Test
    void anOrderConsumptionWritesTheMovementWithTheRealNumbers() {
        InventoryMovementService.StockChange change =
                movements.consumeForOrder(variant.getId(), 3, "sara@example.test", 4242L);

        assertThat(change.before()).isEqualTo(10);
        assertThat(change.after()).isEqualTo(7);

        InventoryMovement row = onlyRow();
        assertThat(row.getStockBefore()).isEqualTo(10);
        assertThat(row.getStockAfter()).isEqualTo(7);
        assertThat(row.getDelta()).isEqualTo(-3);
        assertThat(row.getReason()).isEqualTo(InventoryMovementReason.ORDER);
        assertThat(row.getSourceType()).isEqualTo(InventoryMovementSource.ORDER);
        assertThat(row.getSourceId()).isEqualTo(4242L);
        assertThat(row.getActor()).isEqualTo("sara@example.test");
        assertThat(row.getReasonNote()).isNull();
        assertThat(row.getCreatedAt()).isNotNull();

        // And the shelf really moved.
        assertThat(variantRepository.findStockById(variant.getId())).isEqualTo(7);
    }

    /** A consumption that changes nothing (stock already gone) writes no row. */
    @Test
    void aConsumptionThatCannotHappenWritesNoRow() {
        assertThat(movements.consumeForOrder(variant.getId(), 99, "sara@example.test", 1L)).isNull();
        assertThat(movementRepository.countByVariantId(variant.getId())).isZero();
    }

    /** Saving the product form with a different stock figure is a movement. */
    @Test
    void theProductFormWritesAMovementOnlyWhenTheFigureChanged() {
        variantService.replaceVariants(product, List.of(request(12)));
        entityManager.flush();

        InventoryMovement row = onlyRow();
        assertThat(row.getStockBefore()).isEqualTo(10);
        assertThat(row.getStockAfter()).isEqualTo(12);
        assertThat(row.getReason()).isEqualTo(InventoryMovementReason.PRODUCT_EDIT);
        assertThat(row.getSourceType()).isEqualTo(InventoryMovementSource.PRODUCT);
        assertThat(row.getSourceId()).isEqualTo(product.getId());

        // A save that only touches the price is not a stock change.
        variantService.replaceVariants(product, List.of(request(12, "700.00")));
        entityManager.flush();
        assertThat(movementRepository.countByVariantId(variant.getId())).isEqualTo(1);
    }

    /** A manual adjustment: 10 → 15, with the sentence the admin typed. */
    @Test
    void aManualAdjustmentRecordsTheReasonAndTheActor() {
        InventoryMovementService.StockChange change =
                movements.adjustManually(variant.getId(), 5, "  24 units arrived from the roaster  ", "admin@nocteon.test");

        assertThat(change.before()).isEqualTo(10);
        assertThat(change.after()).isEqualTo(15);

        InventoryMovement row = onlyRow();
        assertThat(row.getReason()).isEqualTo(InventoryMovementReason.MANUAL_ADJUSTMENT);
        assertThat(row.getSourceType()).isEqualTo(InventoryMovementSource.MANUAL);
        assertThat(row.getSourceId()).isNull();
        assertThat(row.getActor()).isEqualTo("admin@nocteon.test");
        // Trimmed: the log is read by people, not by a parser.
        assertThat(row.getReasonNote()).isEqualTo("24 units arrived from the roaster");
        assertThat(row.getDelta()).isEqualTo(5);
    }

    // ─── the refusals ────────────────────────────────────────────────────────

    @Test
    void aManualAdjustmentWithoutAReasonIsRefusedAndWritesNothing() {
        assertThatThrownBy(() -> movements.adjustManually(variant.getId(), 5, "   ", "admin@nocteon.test"))
                .isInstanceOf(StockAdjustmentReasonRequiredException.class);
        assertThatThrownBy(() -> movements.adjustManually(variant.getId(), 5, null, "admin@nocteon.test"))
                .isInstanceOf(StockAdjustmentReasonRequiredException.class);

        assertThat(movementRepository.countByVariantId(variant.getId())).isZero();
        assertThat(variantRepository.findStockById(variant.getId())).isEqualTo(10);
    }

    @Test
    void aZeroOrNegativeResultingAdjustmentIsRefusedAndWritesNothing() {
        assertThatThrownBy(() -> movements.adjustManually(variant.getId(), 0, "typo", "admin"))
                .isInstanceOf(InvalidStockAdjustmentException.class);
        assertThatThrownBy(() -> movements.adjustManually(variant.getId(), -11, "shrinkage", "admin"))
                .isInstanceOf(InvalidStockAdjustmentException.class);

        assertThat(movementRepository.countByVariantId(variant.getId())).isZero();
        assertThat(variantRepository.findStockById(variant.getId())).isEqualTo(10);
    }

    @Test
    void anUnknownVariantIsNotFound() {
        assertThatThrownBy(() -> movements.adjustManually(999_999_999L, 1, "restock", "admin"))
                .isInstanceOf(ProductVariantNotFoundException.class);
    }

    /**
     * A rolled-back change leaves nothing behind.
     *
     * <p>The row is written in the SAME transaction as the update, so an order that
     * fails after consuming its stock (a failed payment link, a constraint) cannot
     * leave a movement describing a change the database undid.
     */
    @Test
    void aRolledBackChangeLeavesNoPhantomRow() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        Long variantId = template.execute(status -> {
            // Everything happens INSIDE the doomed transaction: the variant, the
            // change and the row that explains it. Rolling it back must remove
            // all three — if the row survived, the log would describe a change
            // the shelf never kept.
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            Category category = categoryRepository.save(
                    Category.builder().slug("rollback-cat-" + suffix).isActive(true).build());
            Product rollbackProduct = productRepository.save(Product.builder()
                    .slug("rollback-product-" + suffix)
                    .category(category)
                    .productType(ProductType.COFFEE)
                    .isActive(true)
                    .build());
            ProductVariant rollbackVariant = variantRepository.save(ProductVariant.builder()
                    .product(rollbackProduct)
                    .sku("ROLLBACK-" + suffix)
                    .price(new BigDecimal("10.00"))
                    .stockQuantity(5)
                    .isActive(true)
                    .version(0L)
                    .build());
            entityManager.flush();

            movements.adjustManually(rollbackVariant.getId(), -2, "test rollback", "admin");

            // Written, in this transaction, before the rollback.
            assertThat(movementRepository.countByVariantId(rollbackVariant.getId())).isEqualTo(1);

            status.setRollbackOnly();
            return rollbackVariant.getId();
        });

        assertThat(movementRepository.countByVariantId(variantId)).isZero();
        assertThat(variantRepository.findById(variantId)).isEmpty();
    }

    // ─── the reads ───────────────────────────────────────────────────────────

    /** The panel reads the last changes: newest first, and only this variant's. */
    @Test
    void theHistoryIsNewestFirstAndBelongsToOneVariant() {
        movements.adjustManually(variant.getId(), 1, "first", "admin");
        movements.adjustManually(variant.getId(), 2, "second", "admin");
        movements.adjustManually(variant.getId(), 3, "third", "admin");

        ProductVariant other = variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku(variant.getSku() + "-other")
                .price(new BigDecimal("100.00"))
                .stockQuantity(4)
                .isActive(true)
                .version(0L)
                .build());
        movements.adjustManually(other.getId(), 1, "not mine", "admin");

        List<InventoryMovement> history = movements.recent(variant.getId(), 10);

        assertThat(history).extracting(InventoryMovement::getReasonNote)
                .containsExactly("third", "second", "first");
        assertThat(history).allSatisfy(row -> assertThat(row.getVariantId()).isEqualTo(variant.getId()));
    }

    @Test
    void theReadIsLimitedToTheLastN() {
        for (int i = 1; i <= 5; i++) {
            movements.adjustManually(variant.getId(), 1, "step " + i, "admin");
        }
        assertThat(movements.recent(variant.getId(), 2))
                .extracting(InventoryMovement::getReasonNote)
                .containsExactly("step 5", "step 4");
    }

    // ─── the API ─────────────────────────────────────────────────────────────

    @Test
    void theMovementsEndpointAnswersNewestFirst() throws Exception {
        movements.adjustManually(variant.getId(), 1, "older", "admin");
        movements.adjustManually(variant.getId(), 1, "newer", "admin");

        mockMvc.perform(get("/api/dashboard/products/variants/{id}/movements", variant.getId())
                .param("limit", "1")
                .with(user(principalOf(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].reasonNote").value("newer"))
                .andExpect(jsonPath("$.data[0].actor").value("admin"))
                .andExpect(jsonPath("$.data[0].stockBefore").isNumber());
    }

    @Test
    void theMovementsEndpointNeedsProductRead() throws Exception {
        mockMvc.perform(get("/api/dashboard/products/variants/{id}/movements", variant.getId())
                .with(user("no-products").authorities(new SimpleGrantedAuthority("order:read"))))
                .andExpect(status().isForbidden());
    }

    /** A blank reason over HTTP is a 400 with the backend's own message. */
    @Test
    void theAdjustmentEndpointRefusesABlankReason() throws Exception {
        mockMvc.perform(post("/api/dashboard/products/variants/{id}/stock-adjustment", variant.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"delta\": 5, \"reason\": \"   \"}")
                .with(user(principalOf(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("reason")));

        assertThat(movementRepository.countByVariantId(variant.getId())).isZero();
    }

    @Test
    void theAdjustmentEndpointRecordsTheChangeAndAnswersTheNewStock() throws Exception {
        mockMvc.perform(post("/api/dashboard/products/variants/{id}/stock-adjustment", variant.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"delta\": -4, \"reason\": \"Four bags damaged in transit\"}")
                .with(user(principalOf(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockBefore").value(10))
                .andExpect(jsonPath("$.data.stockAfter").value(6))
                .andExpect(jsonPath("$.data.delta").value(-4))
                .andExpect(jsonPath("$.data.actor").value(admin.getEmail()));

        InventoryMovement row = onlyRow();
        assertThat(row.getReasonNote()).isEqualTo("Four bags damaged in transit");
        assertThat(row.getActor()).isEqualTo(admin.getEmail());
    }

    @Test
    void theAdjustmentEndpointNeedsProductUpdate() throws Exception {
        mockMvc.perform(post("/api/dashboard/products/variants/{id}/stock-adjustment", variant.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"delta\": 5, \"reason\": \"restock\"}")
                .with(user("read-only").authorities(new SimpleGrantedAuthority("product:read"))))
                .andExpect(status().isForbidden());
    }

    // ─── fixtures ────────────────────────────────────────────────────────────

    private InventoryMovement onlyRow() {
        List<InventoryMovement> rows = movementRepository.findAll().stream()
                .filter(row -> variant.getId().equals(row.getVariantId()))
                .toList();
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private ProductVariantRequest request(int stock) {
        return request(stock, "650.00");
    }

    private ProductVariantRequest request(int stock, String price) {
        ProductVariantRequest request = new ProductVariantRequest();
        request.setSku(variant.getSku());
        request.setPrice(new BigDecimal(price));
        request.setStockQuantity(stock);
        request.setIsActive(true);
        request.setOptionValueIds(List.of());
        return request;
    }

    private UserPrincipal principalOf(User user) {
        return UserPrincipal.create(userRepository.findById(user.getId()).orElseThrow());
    }
}
