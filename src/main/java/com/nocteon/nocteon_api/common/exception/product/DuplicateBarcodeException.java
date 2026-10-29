package com.nocteon.nocteon_api.common.exception.product;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

/**
 * FE-15: a barcode is already used by another variant.
 *
 * <p>A 409 rather than a 400: the request is well-formed, it just collides with a value that is
 * already stored — the same shape {@link DuplicateSkuException} has, so the dashboard's save toast
 * shows the message the API localised instead of a generic validation summary.
 *
 * <p>Uniqueness is global, across every product, and includes trashed variants: their rows survive
 * a delete (order history points at them), so the value is still occupied in the column.
 */
public class DuplicateBarcodeException extends BaseApiException {
    public DuplicateBarcodeException() {
        super("error.productVariant.duplicateBarcode", HttpStatus.CONFLICT);
    }
}
