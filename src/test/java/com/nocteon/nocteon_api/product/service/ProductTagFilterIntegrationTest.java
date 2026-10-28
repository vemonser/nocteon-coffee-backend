package com.nocteon.nocteon_api.product.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.category.repository.CategoryRepository;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.product.dto.request.ProductFilterRequest;
import com.nocteon.nocteon_api.product.dto.response.ProductCardResponse;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductTag;
import com.nocteon.nocteon_api.product.entity.ProductTagTranslation;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.product.repository.ProductTagRepository;
import com.nocteon.nocteon_api.product.repository.specification.ProductSpecifications;
import com.nocteon.nocteon_api.support.TestJwtKeys;

import jakarta.persistence.EntityManager;

/**
 * FE-10: {@code ?tagSlug=} returns exactly the products that carry the tag.
 *
 * <p>Runs the real JPQL against the database, because the property being protected here is not
 * "the filter ran" but "the page did not multiply": a tag is many-to-many, and a plain join would
 * return the same product once per link, inflating {@code totalElements} and the rows.
 */
@SpringBootTest(classes = NocteonApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ProductTagFilterIntegrationTest {

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductTagRepository tagRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductService productService;
    @Autowired
    private EntityManager entityManager;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Test
    void listsOnlyTheProductsCarryingTheTagAndNeverRepeatsOne() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ProductTag arrivals = persistTag("new-arrivals-" + suffix, "New arrivals", "وصل حديثاً");
        ProductTag gifts = persistTag("gift-boxes-" + suffix, "Gift boxes", "علب الهدايا");

        Category category = persistCategory("tag-filter-" + suffix);
        Product tagged = persistProduct(category, "tagged-" + suffix, arrivals, gifts);
        Product untagged = persistProduct(category, "untagged-" + suffix, (ProductTag) null);
        // A second product on the same tag proves the filter is not "first match wins".
        Product taggedTwice = persistProduct(category, "tagged-twice-" + suffix, arrivals);

        Page<Product> filtered = findAll(tagFilter(arrivals.getSlug()));

        List<String> slugs = filtered.getContent().stream().map(Product::getSlug).toList();
        assertThat(slugs).contains(tagged.getSlug(), taggedTwice.getSlug());
        assertThat(slugs).doesNotContain(untagged.getSlug());
        // Two products, two links: the count must be two, not three.
        assertThat(slugs).doesNotHaveDuplicates();
        assertThat(filtered.getTotalElements()).isEqualTo(filtered.getContent().size());

        Page<Product> everything = findAll(new ProductFilterRequest());
        assertThat(everything.getContent()).extracting(Product::getSlug)
                .contains(tagged.getSlug(), taggedTwice.getSlug(), untagged.getSlug());
        // And the gift-boxes product is not swept in by the other tag.
        assertThat(findAll(tagFilter(gifts.getSlug())).getContent())
                .extracting(Product::getSlug)
                .containsExactly(tagged.getSlug());
    }

    /**
     * The dashboard list does not go through the specification — it has its own paginated JPQL —
     * so the filter has to work there too, or the select would be a decoration.
     */
    @Test
    void theDashboardListFiltersByIdenticalRules() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ProductTag arrivals = persistTag("new-arrivals-" + suffix, "New arrivals", "وصل حديثاً");
        Category category = persistCategory("dashboard-tag-" + suffix);
        Product tagged = persistProduct(category, "dash-tagged-" + suffix, arrivals);
        Product untagged = persistProduct(category, "dash-untagged-" + suffix, (ProductTag) null);

        ProductFilterRequest filter = tagFilter(arrivals.getSlug());
        filter.setSize(100);
        PageResponse<ProductCardResponse> page = productService.getAllDashboard(filter);

        assertThat(page.getContent()).extracting(ProductCardResponse::getSlug)
                .contains(tagged.getSlug())
                .doesNotContain(untagged.getSlug());
        assertThat(page.getTotalElements()).isEqualTo(page.getContent().size());
    }

    private Page<Product> findAll(ProductFilterRequest filter) {
        return productRepository.findAll(
                ProductSpecifications.withFilters(filter, "en", 0),
                PageRequest.of(0, 100));
    }

    private static ProductFilterRequest tagFilter(String slug) {
        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setTagSlug(slug);
        return filter;
    }

    private ProductTag persistTag(String slug, String en, String ar) {
        ProductTag tag = ProductTag.builder().slug(slug).build();
        tag.getTranslations().add(ProductTagTranslation.builder()
                .tag(tag).language("en").name(en).build());
        tag.getTranslations().add(ProductTagTranslation.builder()
                .tag(tag).language("ar").name(ar).build());
        tagRepository.save(tag);
        return tag;
    }

    private Category persistCategory(String slug) {
        Category category = Category.builder().slug(slug).isActive(true).build();
        categoryRepository.save(category);
        return category;
    }

    private Product persistProduct(Category category, String slug, ProductTag... tags) {
        Product product = Product.builder()
                .slug(slug)
                .category(category)
                .productType(ProductType.COFFEE)
                .isActive(true)
                .build();
        product.getTranslations().add(ProductTranslation.builder()
                .product(product).language("en").name(slug).build());
        product.getVariants().add(ProductVariant.builder()
                .product(product)
                .sku("SKU-" + slug)
                .price(new BigDecimal("650.00"))
                .stockQuantity(10)
                .isActive(true)
                .version(0L)
                .build());
        for (ProductTag tag : tags) {
            if (tag != null) {
                product.getTags().add(tag);
            }
        }
        productRepository.save(product);
        entityManager.flush();
        return product;
    }
}
