package com.nocteon.nocteon_api.product.enums;

/**
 * What caused a stock movement, and which entity the id points at.
 *
 * <p>Separate from {@link InventoryMovementReason} on purpose: several reasons
 * can come from the same source (an order both consumes on payment and could,
 * one day, restock on a return), and a reader of the log needs to jump to the
 * order — {@code sourceType + sourceId} is that jump.
 */
public enum InventoryMovementSource {

    /** An order: {@code source_id} is {@code orders.id}. */
    ORDER,

    /** An admin acting on the variant directly: no source row. */
    MANUAL,

    /** A product save: {@code source_id} is {@code products.id}. */
    PRODUCT
}
