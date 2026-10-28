package com.nocteon.nocteon_api.product.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.common.exception.notFound.DuplicateVariantCombinationException;
import com.nocteon.nocteon_api.common.exception.notFound.ProductOptionNotFoundException;
import com.nocteon.nocteon_api.common.exception.invalid.FieldValidationException;
import com.nocteon.nocteon_api.common.exception.product.OptionNotApplicableException;
import com.nocteon.nocteon_api.common.dto.ApiFieldError;
import com.nocteon.nocteon_api.common.exception.product.DuplicateSkuException;

import com.nocteon.nocteon_api.product.dto.request.ProductVariantRequest;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductOptionValue;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.enums.ProductType;
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

        // Update in place by SKU instead of delete-and-recreate. `order_items` references
        // product_variants, so deleting a variant that has ever been ordered fails on the
        // foreign key — and an ordinary save that only touched the description used to do
        // exactly that. Matching by SKU also keeps the stock and the order history attached
        // to the same row.
        Map<String, ProductVariant> existingBySku = productVariantRepository
                .findByProductId(product.getId()).stream()
                .collect(Collectors.toMap(ProductVariant::getSku, v -> v));

        for (ProductVariantRequest v : requests) {
            ProductVariant existing = existingBySku.remove(v.getSku());
            if (existing != null) {
                applyRequest(existing, product, v);
                productVariantRepository.save(existing);
                continue;
            }
            productVariantRepository.findBySku(v.getSku())
                    .filter(other -> !other.getProduct().getId().equals(product.getId()))
                    .ifPresent(other -> {
                        throw new DuplicateSkuException();
                    });
            productVariantRepository.save(buildVariant(product, v));
        }

        // Whatever the request no longer names is retired, not erased: an order line may still
        // point at it, and a hard delete would break the order's history.
        for (ProductVariant removed : existingBySku.values()) {
            removed.softDelete();
            productVariantRepository.save(removed);
        }
    }

    private void applyRequest(ProductVariant variant, Product product, ProductVariantRequest v) {
        variant.setPrice(v.getPrice());
        variant.setCompareAtPrice(v.getCompareAtPrice());
        variant.setCostPrice(v.getCostPrice());
        variant.setStockQuantity(v.getStockQuantity());
        variant.setReorderThreshold(v.getReorderThreshold() != null ? v.getReorderThreshold() : 5);
        variant.setActive(v.getIsActive() != null ? v.getIsActive() : true);
        Set<ProductOptionValue> values = resolveOptionValues(v.getOptionValueIds(), product);
        // Mutate the loaded collection instead of replacing it: Hibernate merges the entity and
        // would call clear() on the new instance, which fails for an immutable Set.
        if (variant.getOptionValues() == null) {
            variant.setOptionValues(new HashSet<>());
        }
        variant.getOptionValues().clear();
        variant.getOptionValues().addAll(values);
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
        Set<ProductOptionValue> optionValues = resolveOptionValues(v.getOptionValueIds(), product);

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

    private Set<ProductOptionValue> resolveOptionValues(List<Long> optionValueIds, Product product) {
        List<ProductOptionValue> found = productOptionValueRepository.findAllById(optionValueIds);

        if (found.size() != optionValueIds.size()) {
            throw new ProductOptionNotFoundException();
        }

        ProductType productType = product.getProductType();
        for (ProductOptionValue ov : found) {
            Set<ProductType> appliesTo = ov.getOption().getAppliesTo();
            if (appliesTo != null && !appliesTo.isEmpty() && !appliesTo.contains(productType)) {
                throw new OptionNotApplicableException();
            }
        }

        return new HashSet<>(found);
    }
}