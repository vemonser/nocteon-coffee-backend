package com.nocteon.nocteon_api.dashboard.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RepeatPurchaseRateDto {
    private long totalCustomers;
    private long repeatCustomers;
    private BigDecimal repeatRate;
}
