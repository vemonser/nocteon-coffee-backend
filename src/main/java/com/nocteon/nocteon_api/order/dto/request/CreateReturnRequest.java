package com.nocteon.nocteon_api.order.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * A return to record: which lines came back, how many of each, and why.
 *
 * <p>Two of the three are mandatory and the third is checked against the order:
 * at least one line ({@code @NotEmpty}), a quantity above zero
 * ({@code @Min(1)}) and a REASON ("why did this come back?" is the answer
 * nobody can reconstruct later). "How many" is capped by what is left of the
 * order line, and that sum lives in the database, so the service enforces it —
 * the column annotation can only say "more than zero".
 *
 * <p>{@code refundId} is optional: it links the return to a refund that has
 * ALREADY been recorded through the existing refund endpoint. The return does
 * not create money movement of its own.
 */
@Getter
@Setter
public class CreateReturnRequest {

    /** Why the goods came back. Required, and trimmed by the service. */
    @NotBlank(message = "{validation.returnReason.notBlank}")
    @Size(max = 500, message = "{validation.reason.size}")
    private String reason;

    /**
     * The refund that pays this return back, when there is one. It must belong
     * to the SAME order — the service refuses a foreign refund.
     */
    private Long refundId;

    @NotEmpty(message = "{validation.returnItems.notEmpty}")
    @Valid
    private List<Item> items;

    /** One returned line: the order item and how many of its units came back. */
    @Getter
    @Setter
    public static class Item {

        @NotNull(message = "{validation.returnItem.orderItemId.notNull}")
        private Long orderItemId;

        @NotNull(message = "{validation.returnItem.quantity.notNull}")
        @Min(value = 1, message = "{validation.returnItem.quantity.positive}")
        private Integer quantity;

        /** "Bag torn", "wrong grind" — only when the return's reason is not enough. */
        @Size(max = 500, message = "{validation.reason.size}")
        private String note;
    }
}
