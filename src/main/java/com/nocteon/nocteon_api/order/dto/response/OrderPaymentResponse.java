package com.nocteon.nocteon_api.order.dto.response;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class OrderPaymentResponse {
    private Long orderId;
    private BigDecimal total;
    private String paymentUrl;
}