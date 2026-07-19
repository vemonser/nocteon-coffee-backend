package com.nocteon.nocteon_api.dashboard.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RevenueByRoastLevelDto {
    private String roastLevelName;
    private BigDecimal revenue;
    private BigDecimal percentage;
}
