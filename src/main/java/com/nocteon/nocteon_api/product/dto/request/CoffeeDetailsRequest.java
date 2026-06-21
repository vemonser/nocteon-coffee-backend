package com.nocteon.nocteon_api.product.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoffeeDetailsRequest {

    private String processingMethodSlug;
    private String coffeeVarietySlug;
    private String altitude;
    private String harvestYear;
    private String story;
}