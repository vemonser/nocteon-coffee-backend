package com.nocteon.nocteon_api.order.dto.request;

import java.util.List;

import com.nocteon.nocteon_api.order.enums.OrderStatus;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * ONE status change for a set of orders (FE-18b).
 *
 * <p>The body is the single-order change minus the per-order parts: there is
 * one target status, one optional note and no {@code notifyCustomer} flag
 * (there is no per-order note to name in the mail, so the bulk path never
 * mails the customer).
 *
 * <p>{@code ids} is capped at 100 and must not be empty: "move nothing" is a
 * client bug, and answering it with 200 would hide it. Duplicates inside the
 * list are collapsed by the service, so an id is attempted at most once.
 */
@Getter
@Setter
public class BulkUpdateOrderStatusRequest {

        @NotEmpty(message = "{validation.bulkStatus.ids.notEmpty}")
        @Size(max = 100, message = "{validation.bulkStatus.ids.size}")
        private List<Long> ids;

        @NotNull(message = "{validation.status.notNull}")
        private OrderStatus status;

        /** Why the statuses changed. Optional, free text, shared by the batch. */
        @Size(max = 500, message = "{validation.note.size}")
        private String note;
}
