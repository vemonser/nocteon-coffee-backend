package com.nocteon.nocteon_api.order.exception;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

/**
 * A return the order cannot carry: an unknown line, more units than were
 * ordered (or than are still returnable), a duplicate line, a refund that
 * belongs to another order, or a cancelled order.
 *
 * <p>Every case is the caller's mistake, so it is a 400 with the backend's own
 * sentence — the dashboard shows that sentence as it is. The optional argument
 * is the number the message needs ("only {0} are left"), so the reader is given
 * the figure instead of "too many".
 */
public class InvalidReturnException extends BaseApiException {

    public InvalidReturnException(String messageKey) {
        super(messageKey, HttpStatus.BAD_REQUEST);
    }

    public InvalidReturnException(String messageKey, String messageArg) {
        super(messageKey, HttpStatus.BAD_REQUEST, messageArg);
    }
}
