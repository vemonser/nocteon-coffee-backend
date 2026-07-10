package com.nocteon.nocteon_api.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RevenueByDayDto {
    private LocalDate date;
    private BigDecimal revenue;
}