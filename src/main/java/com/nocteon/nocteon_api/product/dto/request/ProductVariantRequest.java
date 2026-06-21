package com.nocteon.nocteon_api.product.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
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

    @DecimalMin(value = "0.01", message = "{validation.weight.min}")
    private BigDecimal weight;

    private String grindType;

    @Min(value = 0, message = "{validation.stock.min}")
    private int stock = 0;

    @DecimalMin(value = "0.00", message = "{validation.discount.min}")
    @DecimalMax(value = "100.00", message = "{validation.discount.max}")
    private BigDecimal discount;
}