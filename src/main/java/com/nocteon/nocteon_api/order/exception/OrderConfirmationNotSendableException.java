package com.nocteon.nocteon_api.order.exception;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

/**
 * The order cannot have a confirmation e-mail sent: the customer account behind
 * it has no e-mail address.
 *
 * <p>409 and not 400: the request is well formed and the admin did nothing
 * wrong — the order is missing the one field a mail needs.
 */
public class OrderConfirmationNotSendableException extends BaseApiException {

    public OrderConfirmationNotSendableException() {
        super("error.order.noRecipientEmail", HttpStatus.CONFLICT);
    }
}
