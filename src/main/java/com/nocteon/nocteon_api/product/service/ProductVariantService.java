package com.nocteon.nocteon_api.product.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.common.exception.product.DuplicateSkuException;
import com.nocteon.nocteon_api.product.dto.request.ProductVariantRequest;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductVariantService {

    private final ProductVariantRepository productVariantRepository;

    @Transactional
    public void saveVariants(Product product, List<ProductVariantRequest> requests) {
        for (ProductVariantRequest v : requests) {
            if (productVariantRepository.existsBySku(v.getSku())) {
                throw new DuplicateSkuException();
            }
            productVariantRepository.save(buildVariant(product, v));
        }
    }

    @Transactional
    public void replaceVariants(Product product, List<ProductVariantRequest> requests) {
        productVariantRepository.deleteByProductId(product.getId());
        productVariantRepository.flush();
        for (ProductVariantRequest v : requests) {
            productVariantRepository.findBySku(v.getSku())
                    .filter(existing -> !existing.getProduct().getId().equals(product.getId()))
                    .ifPresent(existing -> {
                        throw new DuplicateSkuException();
                    });
            productVariantRepository.save(buildVariant(product, v));
        }
    }

    private ProductVariant buildVariant(Product product, ProductVariantRequest v) {
        return ProductVariant.builder()
                .product(product)
                .sku(v.getSku())
                .price(v.getPrice())
                .weightGrams(v.getWeightGrams())
                .grindType(v.getGrindType())
                .stockQuantity(v.getStockQuantity())
                .compareAtPrice(v.getCompareAtPrice())
                .isActive(v.getIsActive() != null ? v.getIsActive() : true)
                .build();
    }
}
