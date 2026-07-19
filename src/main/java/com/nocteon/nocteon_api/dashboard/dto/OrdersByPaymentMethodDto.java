package com.nocteon.nocteon_api.dashboard.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrdersByPaymentMethodDto {
    private String paymentMethod;
    private long count;
}
