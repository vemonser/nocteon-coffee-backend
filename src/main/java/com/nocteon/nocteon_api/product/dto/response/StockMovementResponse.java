package com.nocteon.nocteon_api.product.dto.response;

import java.time.Instant;

import com.nocteon.nocteon_api.product.enums.InventoryMovementReason;
import com.nocteon.nocteon_api.product.enums.InventoryMovementSource;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * One line of a variant's stock history, as the variants panel shows it.
 *
 * <p>The numbers are the pair the mutation actually saw — "12 → 7" — not a delta
 * the UI has to add up, and not the CURRENT stock (which would make every old row
 * describe the present instead of what happened).
 */
@Getter
@Setter
@Builder
public class StockMovementResponse {
    private Long id;
    private Long variantId;
    private int delta;
    private int stockBefore;
    private int stockAfter;
    private InventoryMovementReason reason;
    /** The admin's sentence on a manual adjustment; null for the other rows. */
    private String reasonNote;
    /** Who did it: an admin's e-mail, a customer's, or a system worker. */
    private String actor;
    private InventoryMovementSource sourceType;
    private Long sourceId;
    private Instant createdAt;
}
