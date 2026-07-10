package com.nocteon.nocteon_api.dashboard.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserGrowthDto {
    private long totalUsers;
    private long currentPeriodCount;
    private long previousPeriodCount;
    private BigDecimal growthPercentage;
}