package com.nocteon.nocteon_api.product.dto.response;

import java.math.BigDecimal;

import com.nocteon.nocteon_api.product.enums.ProductType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCardResponse {

    private Long id;
    private String slug;

    private String categorySlug;

    private ProductType productType;

    private boolean featured;
    private boolean isActive;

    private String name;
    private String shortDescription;

    private String primaryImageUrl;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Integer discountPercentage;

    private Integer variantCount;
    private Integer variantsCount;
    private Integer totalStock;
    private Integer lowStockVariantCount;

    /**
     * FE-14: the average of the product's COUNTED reviews (not soft-deleted,
     * APPROVED), rounded to one decimal, or null when nothing is counted.
     */
    private Double averageRating;
    /** FE-14: how many reviews the average is built from (0 when none). */
    private Integer reviewCount;

}