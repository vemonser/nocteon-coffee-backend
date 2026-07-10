package com.nocteon.nocteon_api.storeSettings.dto.response;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StoreSettingsResponse {
    private BigDecimal freeShippingThreshold;
}
