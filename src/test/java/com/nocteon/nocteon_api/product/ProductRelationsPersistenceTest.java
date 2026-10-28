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
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.support.TestJwtKeys;

import jakarta.persistence.EntityManager;

/**
 * FE-11: the ordered relation survives a round trip through the database.
 *
 * <p>{@code @OrderColumn} is the whole reason a "You may also like" block can be curated: without
 * it Hibernate returns whatever order the join table happens to give. A mocked service test cannot
 * see the difference, so this one writes the rows, drops the persistence context and reads them
 * back — which is exactly what the wizard does between a save and a reload.
 */
@SpringBootTest(classes = NocteonApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ProductRelationsPersistenceTest {

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private EntityManager entityManager;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Test
    void keepsThePositionColumnOrderAcrossAReload() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category category = persistCategory("relations-" + suffix);
        Product first = persistProduct(category, "rel-first-" + suffix);
        Product second = persistProduct(category, "rel-second-" + suffix);
        Product third = persistProduct(category, "rel-third-" + suffix);
        Product owner = persistProduct(category, "rel-owner-" + suffix);

        owner.getRelatedProducts().add(second);
        owner.getRelatedProducts().add(third);
        owner.getRelatedProducts().add(first);
        productRepository.save(owner);
        entityManager.flush();
        entityManager.clear();

        Product reloaded = productRepository.findBySlug(owner.getSlug()).orElseThrow();

        assertThat(reloaded.getRelatedProducts())
                .extracting(Product::getSlug)
                .containsExactly(second.getSlug(), third.getSlug(), first.getSlug());
    }

    @Test
    void clearingTheRelationRemovesTheRows() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category category = persistCategory("relations-clear-" + suffix);
        Product related = persistProduct(category, "rel-target-" + suffix);
        Product owner = persistProduct(category, "rel-owner2-" + suffix);
        owner.getRelatedProducts().add(related);
        productRepository.save(owner);
        entityManager.flush();

        owner.getRelatedProducts().clear();
        productRepository.save(owner);
        entityManager.flush();
        entityManager.clear();

        assertThat(productRepository.findBySlug(owner.getSlug()).orElseThrow().getRelatedProducts())
                .isEmpty();
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
