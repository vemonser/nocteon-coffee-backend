package com.nocteon.nocteon_api.product.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ProductTranslationResponse {
    private String language;
    private String name;
    private String shortDescription;
    private String description;
}
