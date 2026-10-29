package com.nocteon.nocteon_api.product.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * A manual change to a variant's stock.
 *
 * <p>{@code delta} is the change, not the new number: adding 24 units and
 * correcting 12 down to 9 look the same in a form that posts an absolute value,
 * but they are different sentences in the history, and a delta says which one the
 * admin meant. Negative removes units.
 *
 * <p>{@code reason} is NOT validated here: the service owns that rule, so the
 * refusal comes back as the one localized message the dashboard shows for a
 * missing reason instead of as a field error with a generic key.
 */
@Getter
@Setter
public class StockAdjustmentRequest {

    @NotNull(message = "{validation.inventory.delta.required}")
    private Integer delta;

    /** Why. Required — a movement nobody can explain is the thing the log prevents. */
    private String reason;
}
