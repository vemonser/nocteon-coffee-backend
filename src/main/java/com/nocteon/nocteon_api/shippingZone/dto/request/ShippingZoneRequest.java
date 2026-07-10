package com.nocteon.nocteon_api.shippingZone.dto.request;

import java.math.BigDecimal;
import java.util.Set;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShippingZoneRequest {

    @NotBlank(message = "{validation.name.notBlank}")
    @Size(max = 100)
    private String name;

    @NotNull(message = "{validation.shippingCost.notNull}")
    @DecimalMin(value = "0.0", message = "{validation.amount.positive}")
    private BigDecimal shippingCost;

    @NotEmpty(message = "{validation.cities.notEmpty}")
    private Set<String> cities;

    private boolean active = true;
}