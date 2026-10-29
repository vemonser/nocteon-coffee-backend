package com.nocteon.nocteon_api.product.dto.request;

import java.math.BigDecimal;
import java.util.List;


import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductVariantRequest {

    @NotBlank(message = "{validation.sku.notBlank}")
    private String sku;

    /**
     * FE-15: optional barcode. Unique across every variant when set; blank is stored as
     * {@code null}. The value is accepted as given (no GTIN checksum here) but trimmed and
     * capped at the column length.
     */
    @Size(max = 64, message = "{validation.barcode.size}")
    private String barcode;

    @NotNull
    @DecimalMin(value = "0.01", message = "{validation.price.min}")
    private BigDecimal price;

    private BigDecimal compareAtPrice;

    /** Optional per-variant photo. Blank means "use the product gallery". */
    private String imageUrl;

    @NotNull
    @Min(value = 0, message = "{validation.stock.min}")
    private Integer stockQuantity;

    @DecimalMin(value = "0.0", message = "{validation.price.min}")
    private BigDecimal costPrice;

    @Min(value = 0, message = "{validation.stock.min}")
    private Integer reorderThreshold;

    @NotNull
    private Boolean isActive;

    @NotEmpty(message = "{validation.optionValues.notEmpty}")
    private List<Long> optionValueIds;
}