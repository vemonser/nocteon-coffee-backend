package com.nocteon.nocteon_api.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.category.repository.CategoryRepository;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.exception.product.DuplicateBarcodeException;
import com.nocteon.nocteon_api.product.dto.request.ProductFilterRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductTranslationRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductVariantRequest;
import com.nocteon.nocteon_api.product.dto.response.DashboardProductResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductCardResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductVariantResponse;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductOption;
import com.nocteon.nocteon_api.product.entity.ProductOptionValue;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.product.repository.ProductOptionRepository;
import com.nocteon.nocteon_api.product.repository.ProductOptionValueRepository;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;
import com.nocteon.nocteon_api.support.TestJwtKeys;

import jakarta.persistence.EntityManager;

/**
 * FE-15: a variant barcode is optional, unique when set, and searchable.
 *
 * <p>Runs against the real schema because the properties under test are database ones: a partial
 * unique index that must ignore NULLs (a plain UNIQUE constraint would allow exactly one
 * barcode-less variant), and a JPQL {@code EXISTS} the dashboard list runs to find a product from
 * a scanned code. Unit mocks would pin the calls and prove neither.
 */
@SpringBootTest(classes = NocteonApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ProductVariantBarcodeIntegrationTest {

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductOptionRepository productOptionRepository;
    @Autowired
    private ProductOptionValueRepository productOptionValueRepository;
    @Autowired
    private ProductVariantRepository variantRepository;
    @Autowired
    private ProductVariantService variantService;
    @Autowired
    private ProductMutationService mutationService;
    @Autowired
    private ProductService productService;
    @Autowired
    private EntityManager entityManager;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    /**
     * The whole path the wizard takes: {@code POST /api/dashboard/products} → the created product
     * comes back with the barcode on its variant, so the form can show what it saved instead of
     * asking the server again.
     */
    @Test
    void theCreatedProductCarriesTheBarcodeTheAdminTyped() {
        String suffix = suffix();
        Category category = category("barcode-create-" + suffix);
        ProductOptionValue value = optionValue("barcode-create-option-" + suffix);

        DashboardProductResponse created = mutationService.create(
                productRequest(category.getSlug(), "Created " + suffix,
                        request("CREATE-" + suffix, "  4006381333931 ", value)),
                List.of());

        assertThat(created.getVariants())
                .singleElement()
                .extracting(ProductVariantResponse::getBarcode)
                .isEqualTo("4006381333931");
    }

    /**
     * The API path for a duplicate: the second product is rejected before anything is written, and
     * the rejection is the 409 the dashboard toast reads.
     */
    @Test
    void theCreatedProductRejectsTheBarcodeOfAnExistingVariant() {
        String suffix = suffix();
        Category category = category("barcode-create-dup-" + suffix);
        ProductOptionValue first = optionValue("barcode-create-dup-a-" + suffix);
        ProductOptionValue second = optionValue("barcode-create-dup-b-" + suffix);

        mutationService.create(productRequest(category.getSlug(), "First " + suffix,
                request("CREATE-DUP-A-" + suffix, "4006381333931", first)), List.of());

        assertThatThrownBy(() -> mutationService.create(
                productRequest(category.getSlug(), "Second " + suffix,
                        request("CREATE-DUP-B-" + suffix, "4006381333931", second)),
                List.of()))
                .isInstanceOf(DuplicateBarcodeException.class);
    }

    @Test
    void storesTheBarcodeAndReturnsItOnTheProduct() {
        String suffix = suffix();
        Product product = persistProduct(category("barcode-store-" + suffix), "barcode-store-" + suffix);
        ProductOptionValue value = optionValue("barcode-store-option-" + suffix);

        variantService.replaceVariants(product, List.of(request("BAR-" + suffix, "  4006381333931  ", value)));

        DashboardProductResponse detail = productService.getDashboardBySlug(product.getSlug());
        assertThat(detail.getVariants())
                .singleElement()
                .extracting(ProductVariantResponse::getBarcode)
                // Trimmed on the way in: the admin's stray spaces are not part of the code.
                .isEqualTo("4006381333931");
    }

    @Test
    void rejectsABarcodeAnotherVariantAlreadyOwns() {
        String suffix = suffix();
        Category category = category("barcode-dup-" + suffix);
        Product first = persistProduct(category, "barcode-first-" + suffix);
        Product second = persistProduct(category, "barcode-second-" + suffix);
        ProductOptionValue value = optionValue("barcode-dup-option-" + suffix);

        variantService.replaceVariants(first, List.of(request("DUP-A-" + suffix, "5901234123457", value)));
        entityManager.flush();

        List<ProductVariantRequest> clash = List.of(request("DUP-B-" + suffix, "5901234123457", value));
        assertThatThrownBy(() -> variantService.replaceVariants(second, clash))
                .isInstanceOf(DuplicateBarcodeException.class);
    }

    @Test
    void rejectsTheSameBarcodeTwiceInsideOneRequest() {
        String suffix = suffix();
        Product product = persistProduct(category("barcode-same-request-" + suffix),
                "barcode-same-request-" + suffix);
        ProductOptionValue first = optionValue("barcode-req-a-" + suffix);
        ProductOptionValue second = optionValue("barcode-req-b-" + suffix);

        assertThatThrownBy(() -> variantService.replaceVariants(product, List.of(
                request("TWICE-A-" + suffix, "0123456789012", first),
                request("TWICE-B-" + suffix, "0123456789012", second))))
                .isInstanceOf(DuplicateBarcodeException.class);
    }

    /**
     * The whole point of the partial index: "unique when set" must not mean "only one variant may
     * omit it". Two variants of the same product, neither carrying a code, both have to be
     * accepted — and the rows really reach the database, so the index is what is being tested.
     */
    @Test
    void acceptsTwoVariantsWithoutABarcode() {
        String suffix = suffix();
        Product product = persistProduct(category("barcode-null-" + suffix), "barcode-null-" + suffix);
        ProductOptionValue first = optionValue("barcode-null-a-" + suffix);
        ProductOptionValue second = optionValue("barcode-null-b-" + suffix);

        variantService.replaceVariants(product, List.of(
                request("NULL-A-" + suffix, null, first),
                request("NULL-B-" + suffix, "   ", second)));
        entityManager.flush();

        assertThat(variantRepository.findByProductId(product.getId()))
                .hasSize(2)
                // Blank input is stored as NULL, never '', which is what keeps the two rows
                // compatible with the unique index and with each other.
                .allSatisfy(v -> assertThat(v.getBarcode()).isNull());

        assertThat(productService.getDashboardBySlug(product.getSlug()).getVariants())
                .allSatisfy(v -> assertThat(v.getBarcode()).isNull());
    }

    @Test
    void findsTheProductByItsVariantBarcodeInTheDashboardList() {
        String suffix = suffix();
        Category category = category("barcode-search-" + suffix);
        Product barcoded = persistProduct(category, "barcode-search-" + suffix);
        Product other = persistProduct(category, "barcode-other-" + suffix);
        ProductOptionValue value = optionValue("barcode-search-option-" + suffix);

        String barcode = "8712345678905";
        variantService.replaceVariants(barcoded, List.of(request("SEARCH-" + suffix, barcode, value)));
        variantService.replaceVariants(other, List.of(request("OTHER-" + suffix, "8712345678999", value)));
        entityManager.flush();
        entityManager.clear();

        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setSearch(barcode);
        filter.setSize(100);
        PageResponse<ProductCardResponse> page = productService.getAllDashboard(filter);

        assertThat(page.getContent()).extracting(ProductCardResponse::getSlug)
                .contains(barcoded.getSlug())
                .doesNotContain(other.getSlug());

        // The title branch still works, and a product with one matching variant is listed once:
        // the search is an EXISTS, not a join that would multiply the row per variant.
        ProductFilterRequest byName = new ProductFilterRequest();
        byName.setSearch(barcoded.getSlug());
        byName.setSize(100);
        assertThat(productService.getAllDashboard(byName).getContent())
                .extracting(ProductCardResponse::getSlug)
                .containsExactly(barcoded.getSlug());
    }

    @Test
    void updatingAndClearingTheBarcodeBothWork() {
        String suffix = suffix();
        Product product = persistProduct(category("barcode-update-" + suffix), "barcode-update-" + suffix);
        ProductOptionValue value = optionValue("barcode-update-option-" + suffix);
        String sku = "UPD-" + suffix;

        variantService.replaceVariants(product, List.of(request(sku, "4006381333931", value)));
        entityManager.flush();

        variantService.replaceVariants(product, List.of(request(sku, "4006381333948", value)));
        entityManager.flush();
        assertThat(variantRepository.findByProductId(product.getId()))
                .singleElement()
                .extracting(ProductVariant::getBarcode)
                .isEqualTo("4006381333948");

        variantService.replaceVariants(product, List.of(request(sku, "  ", value)));
        entityManager.flush();
        assertThat(variantRepository.findByProductId(product.getId()))
                .singleElement()
                .extracting(ProductVariant::getBarcode)
                .isNull();

        // And the value it released is free again.
        Product next = persistProduct(category("barcode-reuse-" + suffix), "barcode-reuse-" + suffix);
        ProductOptionValue otherValue = optionValue("barcode-reuse-option-" + suffix);
        variantService.replaceVariants(next, List.of(request("REUSE-" + suffix, "4006381333948", otherValue)));
        entityManager.flush();

        assertThat(productService.getDashboardBySlug(next.getSlug()).getVariants())
                .singleElement()
                .extracting(ProductVariantResponse::getBarcode)
                .isEqualTo("4006381333948");
    }

    @Test
    void reportsTheBarcodesOfAWholePageInOneQuery() {
        String suffix = suffix();
        Category category = category("barcode-export-" + suffix);
        Product product = persistProduct(category, "barcode-export-" + suffix);
        ProductOptionValue first = optionValue("barcode-export-a-" + suffix);
        ProductOptionValue second = optionValue("barcode-export-b-" + suffix);

        variantService.replaceVariants(product, List.of(
                request("EXPORT-A-" + suffix, "4006381333931", first),
                request("EXPORT-B-" + suffix, null, second)));
        entityManager.flush();
        entityManager.clear();

        Map<Long, List<String>> barcodes = productService.getVariantBarcodes(List.of(product.getId()));

        // The barcode-less variant contributes no entry; the product still appears, with one code.
        assertThat(barcodes).containsEntry(product.getId(), List.of("4006381333931"));
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static ProductVariantRequest request(String sku, String barcode, ProductOptionValue value) {
        ProductVariantRequest request = new ProductVariantRequest();
        request.setSku(sku);
        request.setBarcode(barcode);
        request.setPrice(new BigDecimal("650.00"));
        request.setStockQuantity(10);
        request.setIsActive(true);
        request.setOptionValueIds(List.of(value.getId()));
        return request;
    }

    private static ProductRequest productRequest(String categorySlug, String name, ProductVariantRequest variant) {
        ProductRequest request = new ProductRequest();
        request.setCategorySlug(categorySlug);
        request.setProductType(ProductType.COFFEE);
        request.setActive(true);
        request.setTranslations(List.of(
                translation("en", name), translation("ar", "منتج " + name)));
        request.setVariants(List.of(variant));
        request.setMedia(List.of());
        request.setTagSlugs(List.of());
        request.setTastingNoteSlugs(List.of());
        request.setPairingSlugs(List.of());
        return request;
    }

    private static ProductTranslationRequest translation(String language, String name) {
        ProductTranslationRequest translation = new ProductTranslationRequest();
        translation.setLanguage(language);
        translation.setName(name);
        return translation;
    }

    private Category category(String slug) {
        Category category = Category.builder().slug(slug).isActive(true).build();
        categoryRepository.save(category);
        return category;
    }

    private ProductOptionValue optionValue(String slug) {
        ProductOption option = productOptionRepository.save(
                ProductOption.builder()
                        .slug(slug + "-option")
                        .sortOrder(0)
                        // The builder bypasses the field initialisers, and the variant response
                        // mapper reads translations — an empty set is what a real option carries
                        // for a locale it was never translated into.
                        .translations(new java.util.HashSet<>())
                        .build());
        return productOptionValueRepository.save(
                ProductOptionValue.builder()
                        .option(option)
                        .slug(slug)
                        .sortOrder(0)
                        .translations(new java.util.HashSet<>())
                        .build());
    }

    private Product persistProduct(Category category, String slug) {
        Product product = Product.builder()
                .slug(slug)
                .category(category)
                .productType(ProductType.COFFEE)
                .isActive(true)
                .build();
        product.getTranslations().add(ProductTranslation.builder()
                .product(product).language("en").name(slug).build());
        productRepository.save(product);
        entityManager.flush();
        return product;
    }
}
