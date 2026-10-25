package com.nocteon.nocteon_api.product.dto.response;

import java.time.Instant;

import lombok.Builder;

import com.nocteon.nocteon_api.product.enums.ProductType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ProductOptionResponse {
    private Long id;
    private String slug;
    private Integer sortOrder;
    private Instant createdAt;
    private String name;
    private java.util.Set<ProductType> appliesTo;
}