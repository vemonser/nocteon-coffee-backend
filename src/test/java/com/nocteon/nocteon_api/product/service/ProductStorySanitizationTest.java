package com.nocteon.nocteon_api.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nocteon.nocteon_api.ai.service.ProductIndexingService;
import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.category.repository.CategoryRepository;
import com.nocteon.nocteon_api.cloudinary.config.CloudinaryProperties;
import com.nocteon.nocteon_api.common.service.HtmlSanitizer;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.farm.repository.FarmRepository;
import com.nocteon.nocteon_api.origin.repository.OriginRepository;
import com.nocteon.nocteon_api.product.dto.request.ProductRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductTranslationRequest;
import com.nocteon.nocteon_api.product.dto.response.DashboardProductResponse;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.product.mapper.DashboardProductMapper;
import com.nocteon.nocteon_api.product.mapper.ProductResponseMapper;
import com.nocteon.nocteon_api.product.repository.*;

/**
 * FE-08: the story is rich text now, so it is sanitized like the journal body.
 *
 * <p>A plain-text legacy story still passes through (the allowlist keeps text), and dangerous
 * markup never reaches the database, which is what the product page eventually renders.
 */
@ExtendWith(MockitoExtension.class)
class ProductStorySanitizationTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductTranslationRepository productTranslationRepository;
    @Mock private ProductMediaRepository productMediaRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private OriginRepository originRepository;
    @Mock private FarmRepository farmRepository;
    @Mock private LookupServiceHelper helper;
    @Mock private ProductMediaService productMediaService;
    @Mock private ProductVariantService productVariantService;
    @Mock private ProductCoffeeDetailsService productCoffeeDetailsService;
    @Mock private ProductRelationService productRelationService;
    @Mock private ProductIndexingService productIndexingService;
    @Mock private ProductResponseMapper productResponseMapper;
    @Mock private DashboardProductMapper dashboardProductMapper;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private ProductTagService productTagService;

    @Spy
    private HtmlSanitizer htmlSanitizer = new HtmlSanitizer(cloudinaryProperties());

    private ProductMutationService service;

    private static CloudinaryProperties cloudinaryProperties() {
        CloudinaryProperties properties = new CloudinaryProperties();
        properties.setCloudName("nocteon-test");
        return properties;
    }

    @Test
    void storesTheStoryAsSanitizedHtml() {
        service = newService();
        Product existing = Product.builder().id(7L).slug("yirgacheffe").isActive(true).build();
        when(productRepository.findBySlug("yirgacheffe")).thenReturn(Optional.of(existing));
        when(categoryRepository.findBySlug("single-origin"))
                .thenReturn(Optional.of(Category.builder().slug("single-origin").build()));
        when(dashboardProductMapper.buildDashboardResponse(existing))
                .thenReturn(DashboardProductResponse.builder().build());

        ProductRequest request = request("<p>Grown in <strong>Gedeb</strong>.</p>"
                + "<script>alert(1)</script>"
                + "<img src=\"https://evil.example/beacon.png\">");
        service.update("yirgacheffe", request, List.of());

        ArgumentCaptor<ProductTranslation> saved = ArgumentCaptor.forClass(ProductTranslation.class);
        verify(productTranslationRepository, org.mockito.Mockito.times(2)).save(saved.capture());

        assertThat(saved.getAllValues().get(0).getStory())
                .contains("<strong>Gedeb</strong>")
                .doesNotContain("script", "evil.example");
    }

    @Test
    void keepsALegacyPlainTextStory() {
        service = newService();
        Product existing = Product.builder().id(7L).slug("yirgacheffe").isActive(true).build();
        when(productRepository.findBySlug("yirgacheffe")).thenReturn(Optional.of(existing));
        when(categoryRepository.findBySlug("single-origin"))
                .thenReturn(Optional.of(Category.builder().slug("single-origin").build()));
        when(dashboardProductMapper.buildDashboardResponse(existing))
                .thenReturn(DashboardProductResponse.builder().build());

        service.update("yirgacheffe", request("A farm story, written before the editor."), List.of());

        ArgumentCaptor<ProductTranslation> saved = ArgumentCaptor.forClass(ProductTranslation.class);
        verify(productTranslationRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getStory())
                .isEqualTo("A farm story, written before the editor.");
    }

    private ProductMutationService newService() {
        return new ProductMutationService(
                productRepository, productTranslationRepository, productMediaRepository,
                categoryRepository, originRepository, farmRepository, helper, productMediaService,
                productVariantRepository, productResponseMapper, dashboardProductMapper,
                productVariantService, productCoffeeDetailsService, productRelationService,
                productIndexingService, htmlSanitizer, productTagService);
    }

    private static ProductRequest request(String story) {
        ProductRequest request = new ProductRequest();
        request.setCategorySlug("single-origin");
        request.setProductType(ProductType.EQUIPMENT);
        request.setVariants(List.of());
        request.setMedia(List.of());
        request.setTastingNoteSlugs(List.of());
        request.setPairingSlugs(List.of());
        request.setBrewingMethods(List.of());
        request.setTranslations(List.of(
                translation("en", "Yirgacheffe", story),
                translation("ar", "يرغاتشيف", story)));
        return request;
    }

    private static ProductTranslationRequest translation(String language, String name, String story) {
        ProductTranslationRequest translation = new ProductTranslationRequest();
        translation.setLanguage(language);
        translation.setName(name);
        translation.setStory(story);
        return translation;
    }
}
