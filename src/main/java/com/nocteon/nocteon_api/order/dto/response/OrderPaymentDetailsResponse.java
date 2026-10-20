package com.nocteon.nocteon_api.order.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * The payment of an order, in full.
 *
 * <p>The order carries the payment STATUS; the payment row carries the
 * transaction reference and the moment the money arrived, and the refunds
 * carry the amounts that went back. The NET PAID is the total minus the
 * refunds — and it is null while no refund amount is known, because a
 * {@code REFUNDED} flag without an amount cannot be turned into a number.
 */
@Getter
@Setter
@Builder
public class OrderPaymentDetailsResponse {

        private String method;

        private String status;

        /** The gateway's transaction reference, when there is one. */
        private String transactionReference;

        /** When the money arrived. */
        private Instant paidAt;

        private BigDecimal amount;

        /** The total minus the refunds. Null when a refund has no amount yet. */
        private BigDecimal netPaid;

        private List<OrderRefundResponse> refunds;
}
