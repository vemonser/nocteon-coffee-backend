package com.nocteon.nocteon_api.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
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
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductRelation;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.product.repository.ProductRelationRepository;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.product.service.ProductRelationService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

import jakarta.persistence.EntityManager;

/**
 * FE-11: the ordered relation survives a round trip through the database, and re-picking the
 * order actually rewrites it.
 *
 * <p>The reorder case is the one that broke the first implementation: an ordered collection
 * updates positions in place and collides on the primary key as soon as two entries swap. The
 * service replaces the rows instead, and these tests write, drop the persistence context and read
 * back — which is exactly what the wizard does between a save and a reload.
 */
@SpringBootTest(classes = NocteonApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ProductRelationsPersistenceTest {

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductRelationRepository productRelationRepository;
    @Autowired
    private ProductRelationService productRelationService;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private EntityManager entityManager;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Test
    void keepsTheOrderTheAdminPicked() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category category = persistCategory("relations-" + suffix);
        Product first = persistProduct(category, "rel-first-" + suffix);
        Product second = persistProduct(category, "rel-second-" + suffix);
        Product third = persistProduct(category, "rel-third-" + suffix);
        Product owner = persistProduct(category, "rel-owner-" + suffix);
        entityManager.flush();

        productRelationService.replaceRelatedProducts(
                owner, List.of(second.getSlug(), third.getSlug(), first.getSlug()));
        entityManager.flush();
        entityManager.clear();

        assertThat(slugsOf(owner))
                .containsExactly(second.getSlug(), third.getSlug(), first.getSlug());
    }

    @Test
    void reorderingTheSameProductsRewritesThePositions() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category category = persistCategory("relations-order-" + suffix);
        Product first = persistProduct(category, "ord-first-" + suffix);
        Product second = persistProduct(category, "ord-second-" + suffix);
        Product owner = persistProduct(category, "ord-owner-" + suffix);
        entityManager.flush();

        productRelationService.replaceRelatedProducts(owner, List.of(first.getSlug(), second.getSlug()));
        entityManager.flush();

        // Re-pick the order: same two products, swapped.
        productRelationService.replaceRelatedProducts(owner, List.of(second.getSlug(), first.getSlug()));
        entityManager.flush();
        entityManager.clear();

        assertThat(slugsOf(owner)).containsExactly(second.getSlug(), first.getSlug());
    }

    @Test
    void clearingTheRelationRemovesTheRows() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category category = persistCategory("relations-clear-" + suffix);
        Product related = persistProduct(category, "rel-target-" + suffix);
        Product owner = persistProduct(category, "rel-owner2-" + suffix);
        entityManager.flush();

        productRelationService.replaceRelatedProducts(owner, List.of(related.getSlug()));
        entityManager.flush();

        productRelationService.replaceRelatedProducts(owner, List.of());
        entityManager.flush();
        entityManager.clear();

        assertThat(slugsOf(owner)).isEmpty();
    }

    private List<String> slugsOf(Product owner) {
        return productRelationRepository.findByProductIdOrderByPosition(owner.getId()).stream()
                .map(ProductRelation::getRelatedProduct)
                .map(Product::getSlug)
                .toList();
    }

    private Category persistCategory(String slug) {
        Category category = Category.builder().slug(slug).isActive(true).build();
        categoryRepository.save(category);
        return category;
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
        product.getVariants().add(ProductVariant.builder()
                .product(product)
                .sku("SKU-" + slug)
                .price(new BigDecimal("650.00"))
                .stockQuantity(10)
                .isActive(true)
                .version(0L)
                .build());
        productRepository.save(product);
        return product;
    }
}
