package com.nocteon.nocteon_api.payment.exception;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

/**
 * The payment gateway refused a refund.
 *
 * <p>A refund is the money leaving the store. If the gateway says no, the
 * store has NOT given the money back, so nothing is recorded: a refund row
 * without a gateway reference would be a promise the store cannot keep.
 */
public class RefundFailedException extends BaseApiException {

        public RefundFailedException(String message) {
                super("error.refund.failed", HttpStatus.BAD_GATEWAY, message);
        }

        public RefundFailedException(String message, Throwable cause) {
                super("error.refund.failed", HttpStatus.BAD_GATEWAY, message);
                initCause(cause);
        }
}
