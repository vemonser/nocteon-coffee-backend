package com.nocteon.nocteon_api.product.dto.response;

import java.math.BigDecimal;

import com.nocteon.nocteon_api.product.dto.response.summary.LockupResponse;
import com.nocteon.nocteon_api.product.dto.response.summary.RoastLevelSummary;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CoffeeDetailsResponse {
    private String processingMethod;
    private LockupResponse coffeeVariety;
    private BigDecimal altitude;
    private Short harvestYear;
    private String story;
    private RoastLevelSummary roastLevel;
}