package com.nocteon.nocteon_api.cart.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DashboardCartResponse {
    private Long id;
    private Long userId;
    private String userEmail;
    private String userFullName;
    private String avatarUrl;
    private List<DashboardCartItemResponse> items;
    private BigDecimal total;
    private int itemCount;
    private int totalUnits;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastActivityAt;
    private Instant lastRemindedAt;
    private String status;
    private Long convertedOrderId;
    private BigDecimal estimatedShipping;
    private boolean qualifiesForFreeShipping;
    private BigDecimal freeShippingThreshold;
    private String currency;
    private boolean hasItems;
}