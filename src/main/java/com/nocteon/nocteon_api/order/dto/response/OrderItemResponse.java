package com.nocteon.nocteon_api.order.dto.response;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class OrderItemResponse {
    private Long id;
    private Long variantId;
    private String sku;
    private String productSlug;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
}
