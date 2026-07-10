package com.nocteon.nocteon_api.promoCode.dto.request;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PromoCodeCalculationResult {
    private Long promoCodeId;
    private BigDecimal discountAmount;
    private boolean freeShipping;
}