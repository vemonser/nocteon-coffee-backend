package com.nocteon.nocteon_api.order.exception;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

/**
 * A decision that contradicts the one already recorded.
 *
 * <p>Repeating the SAME decision is not a conflict — it is a retry, and the
 * service answers with the row unchanged instead of restocking twice. Asking to
 * APPROVE a REJECTED return (or REJECT an APPROVED one) is a different thing:
 * it would rewrite history, so it is a 409.
 */
public class ReturnAlreadyDecidedException extends BaseApiException {

    public ReturnAlreadyDecidedException() {
        super("error.return.alreadyDecided", HttpStatus.CONFLICT);
    }
}
