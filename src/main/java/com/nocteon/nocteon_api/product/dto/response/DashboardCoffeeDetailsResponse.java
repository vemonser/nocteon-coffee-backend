package com.nocteon.nocteon_api.product.dto.response;

import java.math.BigDecimal;


import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DashboardCoffeeDetailsResponse {
    private String processingMethodSlug;
    private String coffeeVarietySlug;
    private BigDecimal altitude;
    private Short harvestYear;
    private String story;

    private String roastLevelSlug;
}
