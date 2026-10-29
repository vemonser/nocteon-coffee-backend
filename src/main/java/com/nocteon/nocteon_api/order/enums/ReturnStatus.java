package com.nocteon.nocteon_api.order.enums;

/**
 * Where a return stands.
 *
 * <p>Three values, and the UI reaches all three: a return is REQUESTED by an
 * admin, and then decided once — APPROVED or REJECTED. Both decisions are
 * terminal.
 *
 * <p>There is deliberately no RECEIVED and no COMPLETED. The store has no
 * warehouse intake step to reach them and no refund state machine to mirror,
 * and a status nothing can move a row into is a value that exists only to look
 * complete. If a receiving step is ever built, it gets its own transition and
 * its own row in this enum then.
 */
public enum ReturnStatus {

    /** Recorded, waiting for a decision. Stock has NOT moved. */
    REQUESTED,

    /** Approved: the units went back into stock (reason RETURN, FE-13). */
    APPROVED,

    /** Refused: nothing was restocked and the decision is final. */
    REJECTED
}
