package com.nocteon.nocteon_api.order.exception;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

/**
 * A refund the order cannot carry: more money than was paid, or a refund of
 * an order that was never paid.
 */
public class InvalidRefundException extends BaseApiException {

        public InvalidRefundException(String messageKey) {
                super(messageKey, HttpStatus.BAD_REQUEST);
        }
}
