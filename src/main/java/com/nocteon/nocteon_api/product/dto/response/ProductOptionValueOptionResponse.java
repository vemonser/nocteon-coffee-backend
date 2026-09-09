package com.nocteon.nocteon_api.product.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ProductOptionValueOptionResponse {
    private Long id;
    private String slug;
    private String value;
}