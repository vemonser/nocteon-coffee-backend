package com.nocteon.nocteon_api.dashboard.dto;

import com.nocteon.nocteon_api.order.enums.OrderStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrdersByStatusDto {
    private OrderStatus status;
    private long count;
}