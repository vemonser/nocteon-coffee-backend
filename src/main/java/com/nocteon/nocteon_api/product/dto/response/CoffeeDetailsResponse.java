package com.nocteon.nocteon_api.product.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CoffeeDetailsResponse {
    private String processingMethod;
    private String coffeeVariety;
    private String altitude;
    private String harvestYear;
    private String story;
}