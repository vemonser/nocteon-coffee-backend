package com.nocteon.nocteon_api.common.util;

import java.util.Set;

import org.springframework.data.domain.Sort;

public class ProductSortResolver {

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