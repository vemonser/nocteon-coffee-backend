package com.nocteon.nocteon_api.promoCode.dto.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.nocteon.nocteon_api.promoCode.enums.PromoCodeDiscountType;
import com.nocteon.nocteon_api.promoCode.enums.PromoScopeType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PromoCodeRequest {

    @NotBlank(message = "{validation.code.notBlank}")
    @Size(max = 50)
    private String code;

    @NotNull(message = "{validation.discountType.notNull}")
    private PromoCodeDiscountType discountType;

    private BigDecimal discountValue;

    private BigDecimal minOrderAmount;

    @NotNull(message = "{validation.scopeType.notNull}")
    private PromoScopeType scopeType;

    private List<String> categorySlugs;

    private Integer maxTotalRedemptions;

    @Min(1)
    private Integer maxRedemptionsPerUser = 1;

    @NotNull(message = "{validation.validFrom.notNull}")
    private Instant validFrom;

    @NotNull(message = "{validation.validUntil.notNull}")
    private Instant validUntil;

    private boolean active = true;
}