package com.nocteon.nocteon_api.product.dto.request;

import java.math.BigDecimal;

import com.nocteon.nocteon_api.product.enums.GrindType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductVariantRequest {

    @NotBlank(message = "{validation.sku.notBlank}")
    private String sku;

    @NotNull(message = "{validation.price.notNull}")
    @DecimalMin(value = "0.01", message = "{validation.price.min}")
    private BigDecimal price;

    private BigDecimal compareAtPrice;

    @DecimalMin(value = "0.01", message = "{validation.weight.min}")
    private BigDecimal weightGrams;

    private GrindType  grindType;

    @Min(value = 0, message = "{validation.stock.min}")
    private Integer stockQuantity;

    private Boolean isActive;
}