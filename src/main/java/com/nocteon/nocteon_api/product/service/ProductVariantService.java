package com.nocteon.nocteon_api.product.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.common.exception.notFound.DuplicateVariantCombinationException;
import com.nocteon.nocteon_api.common.exception.notFound.ProductOptionNotFoundException;
import com.nocteon.nocteon_api.common.exception.product.DuplicateSkuException;

import com.nocteon.nocteon_api.product.dto.request.ProductVariantRequest;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductOptionValue;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.repository.ProductOptionValueRepository;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductVariantService {

    private final ProductVariantRepository productVariantRepository;
    private final ProductOptionValueRepository productOptionValueRepository;

    @Transactional
    public void saveVariants(Product product, List<ProductVariantRequest> requests) {
        assertNoDuplicateCombinations(requests);

        for (ProductVariantRequest v : requests) {
            if (productVariantRepository.existsBySku(v.getSku())) {
                throw new DuplicateSkuException();
            }
            productVariantRepository.save(buildVariant(product, v));
        }
    }

    @Transactional
    public void replaceVariants(Product product, List<ProductVariantRequest> requests) {
        assertNoDuplicateCombinations(requests);

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

    private void assertNoDuplicateCombinations(List<ProductVariantRequest> requests) {
        Set<Set<Long>> seenCombinations = new HashSet<>();

        for (ProductVariantRequest v : requests) {
            Set<Long> combination = Set.copyOf(v.getOptionValueIds());

            if (!seenCombinations.add(combination)) {
                throw new DuplicateVariantCombinationException();
            }
        }
    }

    private ProductVariant buildVariant(Product product, ProductVariantRequest v) {
        Set<ProductOptionValue> optionValues = resolveOptionValues(v.getOptionValueIds());

        return ProductVariant.builder()
                .product(product)
                .sku(v.getSku())
                .price(v.getPrice())
                .stockQuantity(v.getStockQuantity())
                .costPrice(v.getCostPrice())
                .reorderThreshold(v.getReorderThreshold() != null ? v.getReorderThreshold() : 5)
                .compareAtPrice(v.getCompareAtPrice())
                .isActive(v.getIsActive() != null ? v.getIsActive() : true)
                .optionValues(optionValues)
                .build();
    }

    private Set<ProductOptionValue> resolveOptionValues(List<Long> optionValueIds) {
        List<ProductOptionValue> found = productOptionValueRepository.findAllById(optionValueIds);

        if (found.size() != optionValueIds.size()) {
            throw new ProductOptionNotFoundException();
        }

        return Set.copyOf(found);
    }
}