package com.nocteon.nocteon_api.storeSettings.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class StoreSettingsResponse {
    private Long id;
    private BigDecimal freeShippingThreshold;
    private Instant createdAt;
    private Instant updatedAt;
}
