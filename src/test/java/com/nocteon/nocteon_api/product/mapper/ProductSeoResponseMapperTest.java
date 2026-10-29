package com.nocteon.nocteon_api.product.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.nocteon.nocteon_api.cloudinary.config.CloudinaryProperties;
import com.nocteon.nocteon_api.common.service.HtmlSanitizer;
import com.nocteon.nocteon_api.product.dto.response.ProductMediaResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductResponse;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductMedia;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.enums.MediaType;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.product.repository.CoffeeDetailsRepository;
import com.nocteon.nocteon_api.product.repository.ProductBrewingMethodRepository;
import com.nocteon.nocteon_api.product.repository.ProductMediaRepository;
import com.nocteon.nocteon_api.product.entity.ProductRelation;
import com.nocteon.nocteon_api.product.repository.ProductRelationRepository;
import com.nocteon.nocteon_api.product.repository.ProductTranslationRepository;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;
import com.nocteon.nocteon_api.review.repository.ReviewRepository;
import com.nocteon.nocteon_api.review.service.ReviewService;

/**
 * FE-07: what the public product endpoint says about SEO.
 *
 * <p>The storefront reads these three fields to build {@code <title>}, the meta description and
 * {@code og:image}. A mapper that forgets them is silent — the page just keeps the site-wide
 * defaults — so the assertions are on the response object itself.
 */
@ExtendWith(MockitoExtension.class)
class ProductSeoResponseMapperTest {

    private static final String PRIMARY_IMAGE =
            "https://res.cloudinary.com/nocteon-test/image/upload/v1/nocteon/products/primary.jpg";
    private static final String SECOND_IMAGE =
            "https://res.cloudinary.com/nocteon-test/image/upload/v1/nocteon/products/second.jpg";
    private static final String CUSTOM_OG =
            "https://res.cloudinary.com/nocteon-test/image/upload/v1/nocteon/products/og.jpg";

    @Mock
    private ProductTranslationRepository productTranslationRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private ProductMediaRepository productMediaRepository;
    @Mock
    private ProductBrewingMethodRepository productBrewingMethodRepository;
    @Mock
    private CoffeeDetailsRepository coffeeDetailsRepository;
    @Mock
    private ProductRelationRepository productRelationRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ReviewService reviewService;
    @Mock
    private LookupResponseMapper lookupResponseMapper;
    @Mock
    private LookupResponseImageMapper lookupResponseImageMapper;

    private ProductResponseMapper mapper;

    private static CloudinaryProperties cloudinaryProperties() {
        CloudinaryProperties properties = new CloudinaryProperties();
        properties.setCloudName("nocteon-test");
        return properties;
    }

    @BeforeEach
    void setUp() {
        mapper = new ProductResponseMapper(
                productTranslationRepository,
                productVariantRepository,
                productMediaRepository,
                productBrewingMethodRepository,
                coffeeDetailsRepository,
                reviewRepository,
                reviewService,
                lookupResponseMapper,
                lookupResponseImageMapper,
                new HtmlSanitizer(cloudinaryProperties()),
                new ProductTagMapper(),
                productRelationRepository);
    }

    @Test
    @DisplayName("the language's meta fields and the primary image are exposed")
    void exposesTheLanguageSeoAndTheCoverAsOgImage() {
        Product product = product(null);
        stubReads(product);
        stubDefaultTranslations();
        stubMedia(media(1L, PRIMARY_IMAGE, MediaType.IMAGE, true, 0),
                media(2L, SECOND_IMAGE, MediaType.IMAGE, false, 1));

        ProductResponse response = mapper.buildDetailResponse(product, "ar");

        assertThat(response.getMetaTitle()).isEqualTo("يرغاتشيف | نوكتيون");
        assertThat(response.getMetaDescription()).isEqualTo("قهوة إثيوبية زهرية.");
        assertThat(response.getOgImageUrl()).isEqualTo(PRIMARY_IMAGE);
    }

    @Test
    @DisplayName("an explicit social image wins over the cover")
    void prefersTheCustomSocialImage() {
        Product product = product(CUSTOM_OG);
        stubReads(product);
        stubDefaultTranslations();
        stubMedia(media(1L, PRIMARY_IMAGE, MediaType.IMAGE, true, 0));

        ProductResponse response = mapper.buildDetailResponse(product, "en");

        assertThat(response.getOgImageUrl()).isEqualTo(CUSTOM_OG);
    }

    @Test
    @DisplayName("a video cover is not used as the social image")
    void neverUsesAVideoAsOgImage() {
        Product product = product(null);
        stubReads(product);
        stubMedia(
                media(1L, "clip.mp4", MediaType.VIDEO, true, 0),
                media(2L, SECOND_IMAGE, MediaType.IMAGE, false, 1));

        ProductResponse response = mapper.buildDetailResponse(product, "en");

        assertThat(response.getOgImageUrl()).isEqualTo(SECOND_IMAGE);
    }

    @Test
    @DisplayName("an unset SEO title/description falls back to the name and short description")
    void fallsBackToTheNameAndShortDescription() {
        Product product = product(null);
        stubReads(product);
        stubMedia(media(1L, PRIMARY_IMAGE, MediaType.IMAGE, true, 0));
        when(productTranslationRepository.findByProductId(1L)).thenReturn(List.of(
                ProductTranslation.builder()
                        .language("en")
                        .name("Yirgacheffe")
                        .shortDescription("A floral Ethiopian coffee.")
                        .build()));

        ProductResponse response = mapper.buildDetailResponse(product, "en");

        assertThat(response.getMetaTitle()).isEqualTo("Yirgacheffe");
        assertThat(response.getMetaDescription()).isEqualTo("A floral Ethiopian coffee.");
    }

    @Test
    @DisplayName("related products come back as ready-to-render cards")
    void exposesRelatedProductsAsCards() {
        Product product = product(null);
        stubReads(product);
        stubMedia(media(1L, PRIMARY_IMAGE, MediaType.IMAGE, true, 0));

        com.nocteon.nocteon_api.category.entity.Category category =
                com.nocteon.nocteon_api.category.entity.Category.builder().slug("coffee").build();
        com.nocteon.nocteon_api.product.entity.Product related =
                com.nocteon.nocteon_api.product.entity.Product.builder()
                        .id(9L)
                        .slug("kenya-nyeri")
                        .category(category)
                        .productType(ProductType.COFFEE)
                        .isActive(true)
                        .build();
        related.getTranslations().add(ProductTranslation.builder()
                .product(related).language("en").name("Kenya Nyeri").build());
        related.getMedia().add(media(11L, SECOND_IMAGE, MediaType.IMAGE, true, 0));
        related.getVariants().add(com.nocteon.nocteon_api.product.entity.ProductVariant.builder()
                .id(90L)
                .product(related)
                .sku("KEN-250")
                .price(new java.math.BigDecimal("600.00"))
                .isActive(true)
                .optionValues(java.util.Set.of())
                .build());
        when(productRelationRepository.findByProductIdOrderByPosition(1L)).thenReturn(List.of(
                ProductRelation.builder().product(product).relatedProduct(related).position(0).build()));

        ProductResponse response = mapper.buildDetailResponse(product, "en");

        assertThat(response.getRelatedProducts()).hasSize(1);
        assertThat(response.getRelatedProducts().get(0).getSlug()).isEqualTo("kenya-nyeri");
        assertThat(response.getRelatedProducts().get(0).getName()).isEqualTo("Kenya Nyeri");
        assertThat(response.getRelatedProducts().get(0).getPrimaryImageUrl()).isEqualTo(SECOND_IMAGE);
        assertThat(response.getRelatedProducts().get(0).getMinPrice()).isEqualByComparingTo("600.00");
    }

    @Test
    @DisplayName("a variant's own photo reaches the public response")
    void exposesEachVariantsOwnImage() {
        Product product = product(null);
        stubReads(product);
        stubMedia(media(1L, PRIMARY_IMAGE, MediaType.IMAGE, true, 0));
        when(productVariantRepository.findByProductId(1L)).thenReturn(List.of(
                com.nocteon.nocteon_api.product.entity.ProductVariant.builder()
                        .id(5L)
                        .sku("YIR-250")
                        .price(new java.math.BigDecimal("650.00"))
                        .imageUrl("https://res.cloudinary.com/nocteon-test/image/upload/v1/nocteon/products/variants/250g.jpg")
                        .isActive(true)
                        .optionValues(java.util.Set.of())
                        .build()));

        ProductResponse response = mapper.buildDetailResponse(product, "en");

        assertThat(response.getVariants()).hasSize(1);
        assertThat(response.getVariants().get(0).getImageUrl())
                .isEqualTo("https://res.cloudinary.com/nocteon-test/image/upload/v1/nocteon/products/variants/250g.jpg");
    }

    private static Product product(String ogImageUrl) {
        return Product.builder()
                .id(1L)
                .slug("yirgacheffe")
                .productType(ProductType.EQUIPMENT)
                .isActive(true)
                .ogImageUrl(ogImageUrl)
                .build();
    }

    private void stubReads(Product product) {
        when(productVariantRepository.findByProductId(1L)).thenReturn(List.of());
        when(productBrewingMethodRepository.findByProductId(1L)).thenReturn(List.of());
        when(reviewRepository.findAverageRatingByProductSlug("yirgacheffe")).thenReturn(4.5);
        when(reviewRepository.countByProductSlug("yirgacheffe")).thenReturn(10L);
        Page<com.nocteon.nocteon_api.review.entity.Review> empty = new PageImpl<>(List.of());
        when(reviewRepository.findByProductSlug(eq("yirgacheffe"), any(Pageable.class)))
                .thenReturn(empty);
    }

    private void stubMedia(ProductMedia... items) {
        when(productMediaRepository.findByProductIdOrderBySortOrder(1L))
                .thenReturn(List.of(items));
    }

    private void stubDefaultTranslations() {
        when(productTranslationRepository.findByProductId(1L)).thenReturn(List.of(
                translation("en", "Yirgacheffe", "Yirgacheffe | Nocteon", "A floral Ethiopian coffee."),
                translation("ar", "يرغاتشيف", "يرغاتشيف | نوكتيون", "قهوة إثيوبية زهرية.")));
    }

    private static ProductTranslation translation(
            String language, String name, String metaTitle, String metaDescription) {
        return ProductTranslation.builder()
                .language(language)
                .name(name)
                .metaTitle(metaTitle)
                .metaDescription(metaDescription)
                .build();
    }

    private static ProductMedia media(
            Long id, String url, MediaType type, boolean primary, int sortOrder) {
        return ProductMedia.builder()
                .id(id)
                .url(url)
                .type(type)
                .isPrimary(primary)
                .sortOrder(sortOrder)
                .build();
    }
}
