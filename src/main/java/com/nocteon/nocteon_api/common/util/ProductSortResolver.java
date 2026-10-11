package com.nocteon.nocteon_api.common.util;

import java.util.Set;

import org.springframework.data.domain.Sort;

/**
 * Resolves the {@link Sort} attached to the {@code Pageable} passed to
 * {@code ProductRepository.findAll(spec, pageable)}.
 *
 * <p><strong>The Specification owns the ordering.</strong> {@code ProductSpecifications} calls
 * {@code query.orderBy(...)} for every sort the public listing accepts, including the two that
 * cannot be expressed as a plain column: {@code price} aggregates over variants, and {@code name}
 * aggregates over the per-locale translations. This resolver answers {@link Sort#unsorted()} for
 * those so the Specification's ordering survives.
 *
 * <p>That is load-bearing, not cosmetic. When a {@code Pageable} carries a sort of its own,
 * Spring Data applies it and the Specification's {@code orderBy} stops deciding the result. An
 * earlier version of this class returned {@code createdAt DESC} as a fallback instead of
 * {@code unsorted()}, on the assumption that {@code unsorted()} meant "no ordering at all". It does
 * not — it means "do not override". The fallback silently replaced the aggregate ordering, and
 * {@code ?sort=price&direction=asc} and {@code ...&direction=desc} then returned identical pages.
 */
public class ProductSortResolver {

    /**
     * Fields allowed through to the {@code Pageable}, because they are plain columns on
     * {@code Product} and the Specification orders them identically.
     *
     * <p>{@code name} and {@code price} are deliberately absent: the Specification handles those
     * through {@code min}/{@code max} and {@code least}/{@code greatest} over joined tables.
     */
    private static final Set<String> DIRECT_FIELDS = Set.of("createdAt", "featured");

    public static Sort resolve(String sort, String direction) {
        if (!DIRECT_FIELDS.contains(sort)) {
            return Sort.unsorted();
        }

        Sort.Direction dir = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        return Sort.by(dir, sort);
    }
}