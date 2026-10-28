package com.nocteon.nocteon_api.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nocteon.nocteon_api.ai.service.ProductIndexingService;
import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.category.repository.CategoryRepository;
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
import com.nocteon.nocteon_api.product.repository.ProductMediaRepository;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.product.repository.ProductTranslationRepository;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;

/**
 * FE-07: the SEO columns are actually written.
 *
 * <p>The request fields and the columns exist; the failure this guards against is a mapper
 * that drops them on the floor — the form would save, the admin would see nothing wrong, and
 * every product would keep sharing a generic preview. The assertion is on the entity handed
 * to the repository, i.e. the value that would be persisted.
 */
@ExtendWith(MockitoExtension.class)
class ProductSeoWriteTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductTranslationRepository productTranslationRepository;
    @Mock
    private ProductMediaRepository productMediaRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private OriginRepository originRepository;
    @Mock
    private FarmRepository farmRepository;
    @Mock
    private LookupServiceHelper helper;
    @Mock
    private ProductMediaService productMediaService;
    @Mock
    private ProductVariantService productVariantService;
    @Mock
    private ProductCoffeeDetailsService productCoffeeDetailsService;
    @Mock
    private ProductRelationService productRelationService;
    @Mock
    private ProductIndexingService productIndexingService;
    @Mock
    private ProductResponseMapper productResponseMapper;
    @Mock
    private DashboardProductMapper dashboardProductMapper;
    @Mock
    private ProductVariantRepository productVariantRepository;

    @InjectMocks
    private ProductMutationService service;

    @Test
    void updateWritesTheSeoFieldsOfEveryLanguageAndTheSocialImage() {
        Product existing = Product.builder().id(7L).slug("yirgacheffe").isActive(true).build();
        when(productRepository.findBySlug("yirgacheffe")).thenReturn(Optional.of(existing));
        when(categoryRepository.findBySlug("single-origin"))
                .thenReturn(Optional.of(Category.builder().slug("single-origin").build()));
        when(dashboardProductMapper.buildDashboardResponse(existing))
                .thenReturn(DashboardProductResponse.builder().build());

        service.update("yirgacheffe", request(), List.of());

        assertThat(existing.getOgImageUrl()).isEqualTo(OG_IMAGE);
        assertThat(existing.getOgImageUrl()).isNotBlank();

        ArgumentCaptor<ProductTranslation> captor =
                ArgumentCaptor.forClass(ProductTranslation.class);
        verify(productTranslationRepository, times(2)).save(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(ProductTranslation::getMetaTitle)
                .containsExactly(EN_TITLE, AR_TITLE);
        assertThat(captor.getAllValues())
                .extracting(ProductTranslation::getMetaDescription)
                .containsExactly(EN_DESCRIPTION, AR_DESCRIPTION);
    }

    @Test
    void anExistingTranslationRowIsUpdatedInPlaceWithTheNewSeoValues() {
        Product existing = Product.builder().id(7L).slug("yirgacheffe").isActive(true).build();
        ProductTranslation row = ProductTranslation.builder()
                .product(existing)
                .language("en")
                .name("Yirgacheffe")
                .build();

        when(productRepository.findBySlug("yirgacheffe")).thenReturn(Optional.of(existing));
        when(categoryRepository.findBySlug("single-origin"))
                .thenReturn(Optional.of(Category.builder().slug("single-origin").build()));
        when(productTranslationRepository.findByProductIdAndLanguage(7L, "en"))
                .thenReturn(Optional.of(row));
        when(dashboardProductMapper.buildDashboardResponse(existing))
                .thenReturn(DashboardProductResponse.builder().build());

        service.update("yirgacheffe", request(), List.of());

        assertThat(row.getMetaTitle()).isEqualTo(EN_TITLE);
        assertThat(row.getMetaDescription()).isEqualTo(EN_DESCRIPTION);
    }

    @Test
    void anEmptySocialImageMeansUseTheCoverImageNotAnEmptyString() {
        Product existing = Product.builder().id(7L).slug("yirgacheffe").isActive(true).build();
        when(productRepository.findBySlug("yirgacheffe")).thenReturn(Optional.of(existing));
        when(categoryRepository.findBySlug("single-origin"))
                .thenReturn(Optional.of(Category.builder().slug("single-origin").build()));
        when(dashboardProductMapper.buildDashboardResponse(existing))
                .thenReturn(DashboardProductResponse.builder().build());

        ProductRequest request = request();
        request.setOgImageUrl("   ");
        service.update("yirgacheffe", request, List.of());

        assertThat(existing.getOgImageUrl()).isNull();
    }

    @Test
    void createWritesTheSeoFieldsAndTheSocialImageToo() {
        AtomicReference<Product> saved = new AtomicReference<>();
        when(categoryRepository.findBySlug("single-origin"))
                .thenReturn(Optional.of(Category.builder().slug("single-origin").build()));
        when(helper.generateUniqueSlug(anyString(), any())).thenReturn("yirgacheffe");
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(productTranslationRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(productRepository.findBySlug("yirgacheffe"))
                .thenAnswer(invocation -> Optional.of(saved.get()));
        when(dashboardProductMapper.buildDashboardResponse(any(Product.class)))
                .thenReturn(DashboardProductResponse.builder().build());

        service.create(request(), List.of());

        assertThat(saved.get().getOgImageUrl()).isEqualTo(OG_IMAGE);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductTranslation>> captor = ArgumentCaptor.forClass(List.class);
        verify(productTranslationRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(ProductTranslation::getMetaTitle)
                .containsExactly(EN_TITLE, AR_TITLE);
    }

    @Test
    void aWhitespaceMetaFieldIsStoredAsNotSet() {
        Product existing = Product.builder().id(7L).slug("yirgacheffe").isActive(true).build();
        when(productRepository.findBySlug("yirgacheffe")).thenReturn(Optional.of(existing));
        when(categoryRepository.findBySlug("single-origin"))
                .thenReturn(Optional.of(Category.builder().slug("single-origin").build()));
        when(dashboardProductMapper.buildDashboardResponse(existing))
                .thenReturn(DashboardProductResponse.builder().build());

        ProductRequest request = request();
        request.getTranslations().get(0).setMetaTitle("   ");
        request.getTranslations().get(0).setMetaDescription(" ");
        service.update("yirgacheffe", request, List.of());

        ArgumentCaptor<ProductTranslation> captor =
                ArgumentCaptor.forClass(ProductTranslation.class);
        verify(productTranslationRepository, times(2)).save(captor.capture());
        ProductTranslation first = captor.getAllValues().get(0);
        assertThat(first.getMetaTitle()).isNull();
        assertThat(first.getMetaDescription()).isNull();
    }

    private static final String OG_IMAGE =
            "https://res.cloudinary.com/nocteon-test/image/upload/v1/nocteon/products/og.jpg";
    private static final String EN_TITLE = "Yirgacheffe | Nocteon";
    private static final String AR_TITLE = "يرغاتشيف | نوكتيون";
    private static final String EN_DESCRIPTION = "A floral Ethiopian coffee, roasted for filter.";
    private static final String AR_DESCRIPTION = "قهوة إثيوبية زهرية، محمصة للفلتر.";

    private static ProductRequest request() {
        ProductRequest request = new ProductRequest();
        request.setCategorySlug("single-origin");
        request.setProductType(ProductType.EQUIPMENT);
        request.setVariants(List.of());
        request.setMedia(List.of());
        request.setTastingNoteSlugs(List.of());
        request.setPairingSlugs(List.of());
        request.setBrewingMethods(List.of());
        request.setOgImageUrl(OG_IMAGE);
        request.setTranslations(List.of(
                translation("en", "Yirgacheffe", EN_TITLE, EN_DESCRIPTION),
                translation("ar", "يرغاتشيف", AR_TITLE, AR_DESCRIPTION)));
        return request;
    }

    private static ProductTranslationRequest translation(
            String language, String name, String metaTitle, String metaDescription) {
        ProductTranslationRequest translation = new ProductTranslationRequest();
        translation.setLanguage(language);
        translation.setName(name);
        translation.setMetaTitle(metaTitle);
        translation.setMetaDescription(metaDescription);
        return translation;
    }
}
