package com.nocteon.nocteon_api.dashboard.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RefundRateDto {
    private long totalPaid;
    private long totalRefunded;
    private BigDecimal refundRate;
}
