package com.nocteon.nocteon_api.order.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * A refund: money that is given back.
 *
 * <p>The amount is the amount: without it, the net paid of an order cannot be
 * computed and the dashboard can only say "a refund happened".
 */
@Getter
@Setter
public class CreateRefundRequest {

        @NotNull(message = "{validation.refundAmount.notNull}")
        @DecimalMin(value = "0.01", message = "{validation.refundAmount.positive}")
        private BigDecimal amount;

        @Size(max = 500, message = "{validation.reason.size}")
        private String reason;
}
