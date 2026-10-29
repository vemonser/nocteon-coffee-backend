package com.nocteon.nocteon_api.common.exception.invalid;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

/**
 * FE-11: a product carries at most six "You may also like" entries.
 *
 * <p>The list is short on purpose — it is a merchandising pick, not a catalogue dump — so the
 * endpoint refuses a seventh instead of truncating it silently.
 */
public class TooManyRelatedProductsException extends BaseApiException {
    public static final int MAX_RELATED_PRODUCTS = 6;

    public TooManyRelatedProductsException() {
        super("error.product.related.tooMany", HttpStatus.BAD_REQUEST,
                String.valueOf(MAX_RELATED_PRODUCTS));
    }
}
