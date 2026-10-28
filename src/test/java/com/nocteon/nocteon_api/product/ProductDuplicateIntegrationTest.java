package com.nocteon.nocteon_api.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
import com.nocteon.nocteon_api.product.dto.response.DashboardProductResponse;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductMedia;
import com.nocteon.nocteon_api.product.entity.ProductTag;
import com.nocteon.nocteon_api.product.entity.ProductTagTranslation;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.enums.MediaType;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.product.repository.ProductTagRepository;
import com.nocteon.nocteon_api.product.service.ProductService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

import jakarta.persistence.EntityManager;

/**
 * FE-12: the copy is complete, and three things are deliberately different.
 *
 * <p>Asserted against the real database because the failures here are constraint failures: a
 * copied SKU collides on {@code product_variants.sku}, a copied slug collides on
 * {@code products.slug}, and a half-copied graph only shows up when it is read back. Stock is 0
 * on purpose — duplicating a row does not put another bag on the shelf.
 */
@SpringBootTest(classes = NocteonApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ProductDuplicateIntegrationTest {

    @Autowired
    private ProductService productService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductTagRepository tagRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private EntityManager entityManager;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Test
    void copiesTheGraphAndKeepsTheOriginalIntact() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category category = persistCategory("dup-" + suffix);
        ProductTag tag = persistTag("dup-tag-" + suffix);
        Product related = persistProduct(category, "dup-related-" + suffix);
        Product source = persistSource(category, "dup-source-" + suffix, tag, related);
        entityManager.flush();

        DashboardProductResponse copyResponse = productService.duplicate(source.getSlug());
        entityManager.flush();
        entityManager.clear();

        Product copy = productRepository.findBySlug(copyResponse.getSlug()).orElseThrow();

        assertThat(copy.getSlug()).isEqualTo(source.getSlug() + "-copy");
        assertThat(copy.isActive()).as("a copy must not reach the storefront before it is edited").isFalse();
        assertThat(copy.isFeatured()).isFalse();
        assertThat(copy.getProductType()).isEqualTo(ProductType.COFFEE);

        assertThat(copy.getTranslations()).hasSize(2);
        assertThat(copy.getTranslations()).extracting(ProductTranslation::getName)
                .containsExactlyInAnyOrder("Source EN", "Source AR");
        // The story is rich text; it is copied verbatim, not re-sanitized into something else.
        assertThat(copy.getTranslations()).extracting(ProductTranslation::getStory)
                .allMatch(story -> story != null && story.contains("<strong>"));

        assertThat(copy.getVariants()).hasSize(2);
        assertThat(copy.getVariants()).extracting(ProductVariant::getSku)
                .containsExactlyInAnyOrder("SKU-" + suffix + "-a-copy", "SKU-" + suffix + "-b-copy");
        assertThat(copy.getVariants()).allSatisfy(variant -> {
            assertThat(variant.getStockQuantity()).as("copying a row must not copy inventory").isZero();
            assertThat(variant.getReservedQuantity()).isZero();
            assertThat(variant.getPrice()).isEqualByComparingTo("650.00");
        });

        assertThat(copy.getMedia()).hasSize(2);
        assertThat(copy.getMedia()).filteredOn(ProductMedia::isPrimary).hasSize(1);

        assertThat(copy.getTags()).extracting(ProductTag::getSlug).containsExactly(tag.getSlug());
        assertThat(copy.getRelatedProducts()).extracting(Product::getSlug)
                .containsExactly(related.getSlug());

        // The source is untouched.
        Product reloadedSource = productRepository.findBySlug(source.getSlug()).orElseThrow();
        assertThat(reloadedSource.isActive()).isTrue();
        assertThat(reloadedSource.getVariants()).extracting(ProductVariant::getSku)
                .containsExactlyInAnyOrder("SKU-" + suffix + "-a", "SKU-" + suffix + "-b");
        assertThat(reloadedSource.getVariants()).allSatisfy(variant ->
                assertThat(variant.getStockQuantity()).isEqualTo(10));
    }

    @Test
    void aSecondCopyGetsItsOwnSlugAndSku() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category category = persistCategory("dup2-" + suffix);
        Product source = persistSource(category, "dup2-source-" + suffix, null, null);
        entityManager.flush();

        productService.duplicate(source.getSlug());
        entityManager.flush();
        DashboardProductResponse second = productService.duplicate(source.getSlug());
        entityManager.flush();
        // Read the copy back from the database: the in-memory copy is the entity the service
        // built, and its collections are whatever the service attached, not what a query returns.
        entityManager.clear();

        assertThat(second.getSlug()).isEqualTo(source.getSlug() + "-copy-2");
        assertThat(productRepository.findBySlug(second.getSlug()).orElseThrow().getVariants())
                .extracting(ProductVariant::getSku)
                .contains("SKU-" + suffix + "-a-copy-2");
    }

    private Category persistCategory(String slug) {
        Category category = Category.builder().slug(slug).isActive(true).build();
        categoryRepository.save(category);
        return category;
    }

    private ProductTag persistTag(String slug) {
        ProductTag tag = ProductTag.builder().slug(slug).build();
        tag.getTranslations().add(ProductTagTranslation.builder()
                .tag(tag).language("en").name(slug).build());
        tag.getTranslations().add(ProductTagTranslation.builder()
                .tag(tag).language("ar").name(slug).build());
        tagRepository.save(tag);
        return tag;
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
        return product;
    }

    private Product persistSource(Category category, String slug, ProductTag tag, Product related) {
        Product product = Product.builder()
                .slug(slug)
                .category(category)
                .productType(ProductType.COFFEE)
                .isActive(true)
                .featured(true)
                .build();
        product.getTranslations().add(ProductTranslation.builder()
                .product(product).language("en").name("Source EN")
                .story("<p><strong>Farm</strong> story</p>").build());
        product.getTranslations().add(ProductTranslation.builder()
                .product(product).language("ar").name("Source AR")
                .story("<p><strong>قصة</strong> المزرعة</p>").build());
        product.getVariants().add(variant(product, "SKU-" + slug.replace("dup-source-", "").replace("dup2-source-", "") + "-a"));
        product.getVariants().add(variant(product, "SKU-" + slug.replace("dup-source-", "").replace("dup2-source-", "") + "-b"));
        product.getMedia().add(ProductMedia.builder()
                .product(product).url("https://cdn.test/a.jpg").type(MediaType.IMAGE)
                .sortOrder(0).isPrimary(true).build());
        product.getMedia().add(ProductMedia.builder()
                .product(product).url("https://cdn.test/b.jpg").type(MediaType.IMAGE)
                .sortOrder(1).isPrimary(false).build());
        if (tag != null) {
            product.getTags().add(tag);
        }
        if (related != null) {
            product.getRelatedProducts().add(related);
        }
        productRepository.save(product);
        return product;
    }

    private static ProductVariant variant(Product product, String sku) {
        return ProductVariant.builder()
                .product(product)
                .sku(sku)
                .price(new BigDecimal("650.00"))
                .stockQuantity(10)
                .isActive(true)
                .version(0L)
                .build();
    }
}
