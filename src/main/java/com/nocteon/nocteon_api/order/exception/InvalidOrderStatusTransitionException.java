package com.nocteon.nocteon_api.order.exception;

import com.nocteon.nocteon_api.order.enums.OrderStatus;

public class InvalidOrderStatusTransitionException extends RuntimeException {
    private final OrderStatus from;
    private final OrderStatus to;

    public InvalidOrderStatusTransitionException(OrderStatus from, OrderStatus to) {
        super("order.invalidStatusTransition");
        this.from = from;
        this.to = to;
    }

    public OrderStatus getFrom() { return from; }
    public OrderStatus getTo() { return to; }
}