package com.nocteon.nocteon_api.storeSettings.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StoreSettingsRequest {
    @DecimalMin(value = "0.0", message = "{validation.amount.positive}")
    private BigDecimal freeShippingThreshold;
}