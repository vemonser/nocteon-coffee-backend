package com.nocteon.nocteon_api.promoCode.dto.response;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TopPerformingPromoCodeDto {
    private String code;
    private long redemptionCount;
    private BigDecimal totalDiscountGiven;
}