package com.nocteon.nocteon_api.product.dto.response;

import com.nocteon.nocteon_api.product.enums.MediaType;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ProductMediaResponse {
    private Long id;
    private String url;
    private String altText;
    private MediaType type;
    private int sortOrder;
    @JsonProperty("isPrimary")
    private boolean isPrimary;
}
