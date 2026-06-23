package com.nocteon.nocteon_api.order.dto.request;

import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.order.enums.OrderStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderFilterRequest extends BaseFilterRequest {
    private OrderStatus status;
    private Long userId;
}