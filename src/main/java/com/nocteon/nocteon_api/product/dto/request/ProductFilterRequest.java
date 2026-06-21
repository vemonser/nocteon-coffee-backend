package com.nocteon.nocteon_api.product.dto.request;

import com.nocteon.nocteon_api.product.enums.ProductType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductFilterRequest {
    private String categorySlug;
    private String originSlug;
    private ProductType productType;
    private Boolean featured;

    @Min(0)
    private int page = 0;

    @Min(1) @Max(50)
    private int size = 20;

    private String sort = "createdAt";
    private String direction = "desc";
}