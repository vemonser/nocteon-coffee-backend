package com.nocteon.nocteon_api.storeSettings.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StoreSettingsRequest {
    @DecimalMin(value = "0.0", message = "{validation.amount.positive}")
    private BigDecimal freeShippingThreshold;

    @Size(min = 3, max = 3, message = "{validation.currency.length}")
    @Pattern(regexp = "^[A-Z]{3}$", message = "{validation.currency.format}")
    private String currency;

    private Boolean isFreeShippingActive;
}