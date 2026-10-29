package com.nocteon.nocteon_api.common.exception.product;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

/**
 * A manual stock adjustment arrived without a reason.
 *
 * <p>400 and not 409: nothing about the store's state is wrong — the request is
 * missing the one field that makes the change explainable. A row saying "stock
 * went from 12 to 9" with no reason is exactly what the movement log exists to
 * prevent.
 */
public class StockAdjustmentReasonRequiredException extends BaseApiException {
    public StockAdjustmentReasonRequiredException() {
        super("error.inventory.reasonRequired", HttpStatus.BAD_REQUEST);
    }
}
