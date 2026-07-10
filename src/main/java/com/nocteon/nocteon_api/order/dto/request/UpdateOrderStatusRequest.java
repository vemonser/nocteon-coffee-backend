package com.nocteon.nocteon_api.order.dto.request;

import com.nocteon.nocteon_api.order.enums.OrderStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateOrderStatusRequest {
    @NotNull(message = "{validation.status.notNull}")
    private OrderStatus status;
}