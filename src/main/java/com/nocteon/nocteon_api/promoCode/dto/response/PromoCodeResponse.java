package com.nocteon.nocteon_api.promoCode.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.nocteon.nocteon_api.promoCode.enums.PromoCodeDiscountType;
import com.nocteon.nocteon_api.promoCode.enums.PromoScopeType;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PromoCodeResponse {
    private Long id;
    private String code;
    private PromoCodeDiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal minOrderAmount;
    private PromoScopeType scopeType;
    private List<String> categorySlugs;
    private Integer maxTotalRedemptions;
    private Integer maxRedemptionsPerUser;
    private long totalRedemptions;
    private BigDecimal totalDiscountGiven;      
    private BigDecimal usageRate;               
    private Instant validFrom;
    private Instant validUntil;
    private boolean active;
}