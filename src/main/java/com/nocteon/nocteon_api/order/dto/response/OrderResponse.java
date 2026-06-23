package com.nocteon.nocteon_api.order.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.nocteon.nocteon_api.order.enums.OrderStatus;

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
    private String paymentStatus;
    private String notes;
    private List<OrderItemResponse> items;
    private Instant createdAt;
}
