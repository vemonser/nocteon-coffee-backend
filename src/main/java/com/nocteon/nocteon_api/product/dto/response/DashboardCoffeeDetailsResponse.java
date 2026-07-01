package com.nocteon.nocteon_api.product.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DashboardCoffeeDetailsResponse {
    private String processingMethodSlug;
    private String coffeeVarietySlug;
    private String altitude;
    private String harvestYear;
    private String story;

    private RoastLevelSummary roastLevel;
}
