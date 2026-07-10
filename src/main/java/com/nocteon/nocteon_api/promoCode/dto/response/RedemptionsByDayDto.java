package com.nocteon.nocteon_api.promoCode.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RedemptionsByDayDto {
    private LocalDate date;
    private long count;
    private BigDecimal discountAmount;
}
