package com.nocteon.nocteon_api.product.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ProductBrewingMethodResponse {
    private String brewingMethodSlug;
    private String brewingMethodName;
    private int score;
}