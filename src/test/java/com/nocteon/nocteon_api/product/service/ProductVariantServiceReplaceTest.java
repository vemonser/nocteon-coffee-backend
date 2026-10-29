package com.nocteon.nocteon_api.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nocteon.nocteon_api.common.exception.product.DuplicateSkuException;
import com.nocteon.nocteon_api.product.dto.request.ProductVariantRequest;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductOption;
import com.nocteon.nocteon_api.product.entity.ProductOptionValue;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.product.repository.ProductOptionValueRepository;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;

/**
 * Saving a product must not erase its variants.
 *
 * <p>{@code order_items} references {@code product_variants}, so the old
 * delete-and-recreate failed on the foreign key for any product that had ever been ordered —
 * an edit to the description returned a 500. The replacement matches by SKU: kept variants are
 * updated in place (same id, stock and order history), new ones are inserted, and ones the form
 * dropped are soft-deleted rather than erased.
 */
@ExtendWith(MockitoExtension.class)
class ProductVariantServiceReplaceTest {

    @Mock
    private ProductVariantRepository variantRepository;
    @Mock
    private ProductOptionValueRepository optionValueRepository;

    @Test
    void keepsExistingRowsAndOnlySoftDeletesTheDroppedOnes() {
        Product product = Product.builder().id(1L).slug("yirgacheffe")
                .productType(ProductType.COFFEE).build();
        ProductVariant kept = variant(product, 10L, "YIR-250", "650.00");
        ProductVariant dropped = variant(product, 11L, "YIR-1000", "1378.00");

        when(variantRepository.findByProductId(1L)).thenReturn(List.of(kept, dropped));
        when(optionValueRepository.findAllById(List.of(100L)))
                .thenReturn(List.of(optionValue(100L)));
        when(optionValueRepository.findAllById(List.of(101L)))
                .thenReturn(List.of(optionValue(101L)));

        ProductVariantService service = new ProductVariantService(variantRepository, optionValueRepository,
                        new com.nocteon.nocteon_api.product.service.InventoryMovementService(
                                variantRepository,
                                org.mockito.Mockito.mock(com.nocteon.nocteon_api.product.repository.InventoryMovementRepository.class)));
        service.replaceVariants(product, List.of(
                request("YIR-250", "700.00", 100L),
                request("YIR-500", "900.00", 101L)));

        assertThat(kept.getId()).isEqualTo(10L);
        assertThat(kept.getPrice()).isEqualByComparingTo("700.00");
        assertThat(dropped.getDeletedAt()).isNotNull();
        verify(variantRepository, never()).deleteByProductId(any());

        ArgumentCaptor<ProductVariant> saved = ArgumentCaptor.forClass(ProductVariant.class);
        verify(variantRepository, org.mockito.Mockito.atLeast(3)).save(saved.capture());
        assertThat(saved.getAllValues())
                .anySatisfy(v -> assertThat(v.getSku()).isEqualTo("YIR-500"))
                .anySatisfy(v -> assertThat(v.getSku()).isEqualTo("YIR-250"))
                .anySatisfy(v -> {
                    assertThat(v.getSku()).isEqualTo("YIR-1000");
                    assertThat(v.getDeletedAt()).isNotNull();
                });
    }

    @Test
    void refusesASkuThatBelongsToAnotherLiveProduct() {
        Product product = Product.builder().id(1L).slug("yirgacheffe")
                .productType(ProductType.COFFEE).build();
        Product other = Product.builder().id(2L).slug("other").build();
        when(variantRepository.findByProductId(1L)).thenReturn(List.of());
        when(variantRepository.findBySku("TAKEN")).thenReturn(Optional.of(variant(other, 99L, "TAKEN", "1.00")));

        ProductVariantService service = new ProductVariantService(variantRepository, optionValueRepository,
                        new com.nocteon.nocteon_api.product.service.InventoryMovementService(
                                variantRepository,
                                org.mockito.Mockito.mock(com.nocteon.nocteon_api.product.repository.InventoryMovementRepository.class)));

        assertThatThrownBy(() -> service.replaceVariants(product, List.of(request("TAKEN", "5.00", 100L))))
                .isInstanceOf(DuplicateSkuException.class);
    }

    private static ProductVariant variant(Product product, Long id, String sku, String price) {
        return ProductVariant.builder()
                .id(id)
                .product(product)
                .sku(sku)
                .price(new BigDecimal(price))
                .build();
    }

    private static ProductVariantRequest request(String sku, String price, Long optionValueId) {
        ProductVariantRequest request = new ProductVariantRequest();
        request.setSku(sku);
        request.setPrice(new BigDecimal(price));
        request.setStockQuantity(10);
        request.setIsActive(true);
        request.setOptionValueIds(List.of(optionValueId));
        return request;
    }

    private static ProductOptionValue optionValue(Long id) {
        return ProductOptionValue.builder()
                .id(id)
                .option(ProductOption.builder().id(1L).slug("weight").build())
                .build();
    }
}
