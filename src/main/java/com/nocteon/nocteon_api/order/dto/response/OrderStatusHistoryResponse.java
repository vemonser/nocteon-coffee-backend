package com.nocteon.nocteon_api.order.dto.response;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/** One step of an order's status history. */
@Getter
@Setter
@Builder
public class OrderStatusHistoryResponse {

        private Long id;

        private Long orderId;

        /** Null for the first row: the order was placed, it came from nowhere. */
        private String fromStatus;

        private String toStatus;

        private String note;

        /** Who moved it: the e-mail of the admin, or the system. */
        private String changedBy;

        /** Whether the customer was asked to be told about the change. */
        private boolean notifyCustomer;

        /**
         * Set when the row is an EVENT, not a status change — a return's
         * request, approval or rejection (FE-20). Null for a status change.
         */
        private String eventType;

        private Instant createdAt;
}
