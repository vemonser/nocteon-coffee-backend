package com.nocteon.nocteon_api.promoCode.dto.response;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PromoCodeAnalyticsResponse {
    private BigDecimal totalDiscountGivenAllTime;
    private long totalRedemptionsAllTime;
    private long activeCodesCount;
    private List<TopPerformingPromoCodeDto> topPerformingCodes;
    private List<RedemptionsByDayDto> redemptionsByDay;
}