package com.nocteon.nocteon_api.cart.dto.response;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CartStatsResponse {
    private long activeCarts;
    private long abandonedCarts;
    private long convertedCarts;
    private BigDecimal totalPotentialRevenue;
    private BigDecimal averageCartValue;
}
