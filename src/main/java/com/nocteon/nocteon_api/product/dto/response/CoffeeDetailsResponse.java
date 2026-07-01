package com.nocteon.nocteon_api.product.dto.response;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CoffeeDetailsResponse {
    private String processingMethod;
    private String coffeeVariety;
    private BigDecimal altitude;
    private Short harvestYear;
    private String story;
    private RoastLevelSummary roastLevel;
}