package com.nocteon.nocteon_api.shippingZone.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ShippingZoneResponse {
    private Long id;
    private String name;
    private BigDecimal shippingCost;
    private Set<String> cities;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}