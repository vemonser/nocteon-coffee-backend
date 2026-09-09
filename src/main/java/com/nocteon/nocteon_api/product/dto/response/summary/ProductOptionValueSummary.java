package com.nocteon.nocteon_api.product.dto.response.summary;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ProductOptionValueSummary {
    private String optionSlug;
    private String optionName;
    private String valueSlug;
    private String value;
}