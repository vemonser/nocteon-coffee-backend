package com.nocteon.nocteon_api.order.dto.request;

import com.nocteon.nocteon_api.order.enums.OrderStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * A status change.
 *
 * <p>The body carries more than the status: the note that says WHY, the flag
 * that says the customer is to be told, and the reason a cancellation must
 * give.
 */
@Getter
@Setter
public class UpdateOrderStatusRequest {

        @NotNull(message = "{validation.status.notNull}")
        private OrderStatus status;

        /** Why the status changed. Optional, and free text. */
        @Size(max = 500, message = "{validation.note.size}")
        private String note;

        /** Whether the customer is to be told about the change. */
        private boolean notifyCustomer = false;

        /**
         * Why the order is being cancelled.
         *
         * <p>REQUIRED when the new status is CANCELLED: a cancellation without
         * a reason is a question the team cannot answer a week later.
         */
        @Size(max = 500, message = "{validation.reason.size}")
        private String reason;
}
