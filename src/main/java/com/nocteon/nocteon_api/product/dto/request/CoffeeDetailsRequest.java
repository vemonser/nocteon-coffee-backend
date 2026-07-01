package com.nocteon.nocteon_api.product.dto.request;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoffeeDetailsRequest {

    private String processingMethodSlug;
    private String coffeeVarietySlug;
    private BigDecimal altitude;
    private String roastLevelSlug;
    private Short harvestYear;
    private String story;
}