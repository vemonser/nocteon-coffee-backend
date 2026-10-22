package com.nocteon.nocteon_api.cart.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.nocteon.nocteon_api.product.dto.response.summary.ProductOptionValueSummary;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DashboardCartItemResponse {
    private Long id;
    private Long variantId;
    private String sku;
    private String productSlug;
    private String productName;
    private String primaryImageUrl;
    private BigDecimal price;
    private BigDecimal compareAtPrice;
    private Integer discountPercentage;
    private List<ProductOptionValueSummary> optionValues;
    private int quantity;
    private int stockQuantity;
    private int availableStock;
    private BigDecimal subtotal;
    private boolean productIsActive;
    private boolean variantIsActive;
    private boolean outOfStock;
    private boolean lowStock;
}