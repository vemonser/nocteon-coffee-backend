package com.nocteon.nocteon_api.product.dto.request;

import java.math.BigDecimal;
import java.util.List;


import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductVariantRequest {

    @NotBlank(message = "{validation.sku.notBlank}")
    private String sku;

    @NotNull
    @DecimalMin(value = "0.01", message = "{validation.price.min}")
    private BigDecimal price;

    private BigDecimal compareAtPrice;

    @Min(value = 0, message = "{validation.stock.min}")
    private Integer stockQuantity;

    private Boolean isActive;

    @NotEmpty(message = "{validation.optionValues.notEmpty}")
    private List<Long> optionValueIds;
}