package com.nocteon.nocteon_api.common.exception.product;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

/** A stock adjustment that cannot be applied: no change, or below zero. */
public class InvalidStockAdjustmentException extends BaseApiException {
    public InvalidStockAdjustmentException(String messageKey) {
        super(messageKey, HttpStatus.BAD_REQUEST);
    }
}
