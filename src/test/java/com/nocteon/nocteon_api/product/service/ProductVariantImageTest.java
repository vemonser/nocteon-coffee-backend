package com.nocteon.nocteon_api.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nocteon.nocteon_api.product.dto.request.ProductVariantRequest;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductOption;
import com.nocteon.nocteon_api.product.entity.ProductOptionValue;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.product.repository.ProductOptionValueRepository;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;

/**
 * FE-09: a variant keeps its own photo across a save.
 *
 * <p>Two behaviours are pinned here because both are silent when they break. A URL that arrives
 * from the form is stored verbatim (not rewritten, not dropped by the by-SKU merge), and a blank
 * one becomes {@code null} — "no photo of its own" — instead of the empty string, which the
 * storefront would try to load and the product gallery could not be used to replace.
 */
@ExtendWith(MockitoExtension.class)
class ProductVariantImageTest {

        @Mock
        private ProductVariantRepository variantRepository;
        @Mock
        private ProductOptionValueRepository optionValueRepository;

        @Test
        void keepsTheUploadedImageWhenAnExistingVariantIsSavedAgain() {
                Product product = Product.builder().id(1L).slug("yirgacheffe")
                                .productType(ProductType.COFFEE).build();
                ProductVariant existing = ProductVariant.builder()
                                .id(10L)
                                .product(product)
                                .sku("YIR-250")
                                .price(new BigDecimal("650.00"))
                                .imageUrl("https://cdn.nocteon.test/variants/old.jpg")
                                .build();
                when(variantRepository.findByProductId(1L)).thenReturn(List.of(existing));
                when(optionValueRepository.findAllById(List.of(100L)))
                                .thenReturn(List.of(optionValue(100L)));

                ProductVariantService service = new ProductVariantService(variantRepository, optionValueRepository,
                        new com.nocteon.nocteon_api.product.service.InventoryMovementService(
                                variantRepository,
                                org.mockito.Mockito.mock(com.nocteon.nocteon_api.product.repository.InventoryMovementRepository.class)));
                service.replaceVariants(product, List.of(
                                request("YIR-250", "https://cdn.nocteon.test/variants/250g.jpg")));

                assertThat(existing.getImageUrl()).isEqualTo("https://cdn.nocteon.test/variants/250g.jpg");
                assertThat(existing.getId()).isEqualTo(10L);
        }

        @Test
        void storesABlankImageAsNullSoTheGalleryStillApplies() {
                Product product = Product.builder().id(1L).slug("yirgacheffe")
                                .productType(ProductType.COFFEE).build();
                when(variantRepository.findByProductId(1L)).thenReturn(List.of());
                when(variantRepository.findBySku("YIR-500")).thenReturn(java.util.Optional.empty());
                when(optionValueRepository.findAllById(List.of(100L)))
                                .thenReturn(List.of(optionValue(100L)));

                ProductVariantService service = new ProductVariantService(variantRepository, optionValueRepository,
                        new com.nocteon.nocteon_api.product.service.InventoryMovementService(
                                variantRepository,
                                org.mockito.Mockito.mock(com.nocteon.nocteon_api.product.repository.InventoryMovementRepository.class)));
                service.replaceVariants(product, List.of(request("YIR-500", "   ")));

                ArgumentCaptor<ProductVariant> saved = ArgumentCaptor.forClass(ProductVariant.class);
                org.mockito.Mockito.verify(variantRepository).save(saved.capture());
                assertThat(saved.getValue().getImageUrl()).isNull();
        }

        private static ProductVariantRequest request(String sku, String imageUrl) {
                ProductVariantRequest request = new ProductVariantRequest();
                request.setSku(sku);
                request.setPrice(new BigDecimal("650.00"));
                request.setStockQuantity(10);
                request.setIsActive(true);
                request.setImageUrl(imageUrl);
                request.setOptionValueIds(List.of(100L));
                return request;
        }

        private static ProductOptionValue optionValue(Long id) {
                return ProductOptionValue.builder()
                                .id(id)
                                .option(ProductOption.builder().id(1L).slug("weight").build())
                                .build();
        }
}
