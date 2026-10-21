package com.nocteon.nocteon_api.order.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/** A refund: money that was given back. */
@Getter
@Setter
@Builder
public class OrderRefundResponse {

        private Long id;

        private Long orderId;

        private BigDecimal amount;

        private String reason;

        private String createdBy;

        /**
         * True while the money has NOT gone back through the gateway: the
         * store returns it outside the gateway and the row records that it is
         * still owed.
         */
        private boolean manual;

        /** The gateway's own reference, when the money went through one. */
        private String gatewayReference;

        private Instant createdAt;
}
