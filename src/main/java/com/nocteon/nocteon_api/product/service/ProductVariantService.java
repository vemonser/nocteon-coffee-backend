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
import com.nocteon.nocteon_api.common.exception.product.DuplicateBarcodeException;
import com.nocteon.nocteon_api.common.exception.product.DuplicateSkuException;

import com.nocteon.nocteon_api.product.dto.request.ProductVariantRequest;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductOptionValue;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.common.security.ActorResolver;
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
    private final InventoryMovementService inventoryService;

    @Transactional
    public void saveVariants(Product product, List<ProductVariantRequest> requests) {
        assertNoDuplicateCombinations(requests);
        assertNoDuplicateBarcodes(requests);

        for (ProductVariantRequest v : requests) {
            if (productVariantRepository.existsBySku(v.getSku())) {
                throw new DuplicateSkuException();
            }
            assertBarcodeIsFree(normalizeBarcode(v.getBarcode()), null);
            productVariantRepository.save(buildVariant(product, v));
        }
    }

    @Transactional
    public void replaceVariants(Product product, List<ProductVariantRequest> requests) {
        assertNoDuplicateCombinations(requests);
        assertNoDuplicateBarcodes(requests);

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
            // The barcode check excludes the row this request is updating: a variant keeping its
            // own barcode must not collide with itself.
            assertBarcodeIsFree(
                    normalizeBarcode(v.getBarcode()),
                    existing != null ? existing.getId() : null);
            if (existing != null) {
                // FE-13: the form can change the stock figure, and a change of
                // figure is a movement. The BEFORE is read before applyRequest
                // overwrites it; a save that only touched the price writes
                // nothing (see InventoryMovementService.recordProductEdit).
                int stockBefore = existing.getStockQuantity();
                applyRequest(existing, product, v);
                productVariantRepository.save(existing);
                inventoryService.recordProductEdit(existing.getId(), product.getId(),
                        stockBefore, existing.getStockQuantity(), ActorResolver.currentActor());
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
        variant.setImageUrl(normalizeImageUrl(v.getImageUrl()));
        variant.setBarcode(normalizeBarcode(v.getBarcode()));
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

    /**
     * FE-15: two barcodes inside one request must already collide, before either row is written.
     *
     * <p>The database would catch it too (the partial unique index), but only at flush time, as a
     * {@code DataIntegrityViolationException} the admin reads as "something went wrong" — this
     * turns it into the same 409 the rest of the duplicate cases answer with, and names the field.
     */
    private void assertNoDuplicateBarcodes(List<ProductVariantRequest> requests) {
        Set<String> seen = new HashSet<>();

        for (ProductVariantRequest v : requests) {
            String barcode = normalizeBarcode(v.getBarcode());
            if (barcode != null && !seen.add(barcode)) {
                throw new DuplicateBarcodeException();
            }
        }
    }

    /**
     * Rejects a barcode another variant already owns. {@code ownVariantId} is the row the request
     * updates — it is allowed to keep its own value — and {@code null} on the create path.
     *
     * <p>The lookup includes trashed variants: their rows survive a delete, so the value is still
     * taken in the database, and the partial unique index would reject the insert anyway.
     */
    private void assertBarcodeIsFree(String barcode, Long ownVariantId) {
        if (barcode == null) {
            return;
        }
        productVariantRepository.findIdByBarcodeIncludingDeleted(barcode)
                .filter(ownerId -> !ownerId.equals(ownVariantId))
                .ifPresent(ownerId -> {
                    throw new DuplicateBarcodeException();
                });
    }

    private ProductVariant buildVariant(Product product, ProductVariantRequest v) {
        Set<ProductOptionValue> optionValues = resolveOptionValues(v.getOptionValueIds(), product);

        return ProductVariant.builder()
                .product(product)
                .sku(v.getSku())
                .barcode(normalizeBarcode(v.getBarcode()))
                .price(v.getPrice())
                .imageUrl(normalizeImageUrl(v.getImageUrl()))
                .stockQuantity(v.getStockQuantity())
                .costPrice(v.getCostPrice())
                .reorderThreshold(v.getReorderThreshold() != null ? v.getReorderThreshold() : 5)
                .compareAtPrice(v.getCompareAtPrice())
                .isActive(v.getIsActive() != null ? v.getIsActive() : true)
                .optionValues(optionValues)
                // `product_variants.version` is NOT NULL, and the field carries Spring Data's
                // @Version (not jakarta.persistence's), so Hibernate writes NULL for a fresh row
                // instead of seeding 0 — the insert fails on the constraint. The same value the
                // duplicate path already sets, and the same one the column defaults to.
                .version(0L)
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

    /**
     * A blank image URL from the form means "no photo of its own", not the empty string: stored as
     * null so the response and the storefront fall back to the product gallery instead of asking
     * for an empty resource.
     */
    private static String normalizeImageUrl(String url) {
        return url == null || url.isBlank() ? null : url.trim();
    }

    /**
     * FE-15: whitespace is trimmed and a blank value becomes {@code null}.
     *
     * <p>One representation for "no barcode" is what lets the partial unique index accept any
     * number of variants without one — {@code ''} is a real value to a unique index, so a form
     * that posted it would let exactly one barcode-less variant through and reject the second.
     *
     * <p>The value is otherwise kept exactly as given: a GTIN checksum is a validation concern,
     * and a catalog also holds internal codes (Code 128, ITF-14) a checksum would reject.
     */
    private static String normalizeBarcode(String barcode) {
        return barcode == null || barcode.isBlank() ? null : barcode.trim();
    }
}
