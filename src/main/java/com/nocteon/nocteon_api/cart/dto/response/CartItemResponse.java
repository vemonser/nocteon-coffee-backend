package com.nocteon.nocteon_api.cart.dto.response;

import java.math.BigDecimal;

import com.nocteon.nocteon_api.product.enums.GrindType;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CartItemResponse {
    private Long id;
    private Long variantId;
    private String sku;
    private String productSlug;
    private String productName;
    private String primaryImageUrl;
    private BigDecimal price;
    private BigDecimal compareAtPrice;
    private Integer discountPercentage;
    private BigDecimal weightGrams;
    private GrindType grindType;
    private int quantity;
    private BigDecimal subtotal;
}