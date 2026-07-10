package com.nocteon.nocteon_api.order.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.payment.enums.PaymentMethod;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class OrderResponse {
    private Long id;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private PaymentStatus paymentStatus;
    private PaymentMethod paymentMethod;
    private BigDecimal shippingCost;
    private BigDecimal discountAmount;
    private String notes;
    private List<OrderItemResponse> items;
    private Instant createdAt;
}

