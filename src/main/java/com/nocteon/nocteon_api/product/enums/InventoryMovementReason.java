package com.nocteon.nocteon_api.product.enums;

/**
 * Why a stock number changed.
 *
 * <p>Deliberately short. "Restock" is not a value: the store has no restock
 * endpoint and no purchase-order module, so units arriving are an admin
 * adjustment with the reason "24 units arrived" typed in. The sentence is the
 * context; an enum value that nothing can produce would be a lie in the schema.
 */
public enum InventoryMovementReason {

    /** An order took units off the shelf (a confirmed COD order, a paid one). */
    ORDER,

    /** An admin changed the number by hand. The typed reason is mandatory. */
    MANUAL_ADJUSTMENT,

    /** The product form was saved with a different stock figure on a variant. */
    PRODUCT_EDIT
}
