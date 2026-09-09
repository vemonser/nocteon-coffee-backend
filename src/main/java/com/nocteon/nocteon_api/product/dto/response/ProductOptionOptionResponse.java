package com.nocteon.nocteon_api.product.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ProductOptionOptionResponse {
    private Long id;
    private String slug;
    private String name;
    private List<ProductOptionValueOptionResponse> values;
}