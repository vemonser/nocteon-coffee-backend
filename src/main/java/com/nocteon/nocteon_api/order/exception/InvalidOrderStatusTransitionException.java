package com.nocteon.nocteon_api.order.exception;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;
import com.nocteon.nocteon_api.order.enums.OrderStatus;

/**
 * A status change the state machine does not allow.
 *
 * <p>A 409, not a 400: the request is well formed, it asks for something
 * that cannot happen (a delivered order cannot go back to pending).
 */
public class InvalidOrderStatusTransitionException extends BaseApiException {

        private final OrderStatus from;
        private final OrderStatus to;

        public InvalidOrderStatusTransitionException(OrderStatus from, OrderStatus to) {
                super("error.order.invalidStatusTransition", HttpStatus.CONFLICT);
                this.from = from;
                this.to = to;
        }

        public OrderStatus getFrom() {
                return from;
        }

        public OrderStatus getTo() {
                return to;
        }

        @Override
        public Object[] getMessageArgs() {
                return new Object[] { from, to };
        }
}
