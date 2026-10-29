package com.nocteon.nocteon_api.common.exception.invalid;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

/**
 * FE-11: "You may also like" cannot point at a product that is not for sale.
 *
 * <p>The picker only searches active products, but the API is the boundary: a crafted request
 * could otherwise put an inactive draft on a live product page, where its card and link would
 * lead nowhere.
 */
public class RelatedProductInactiveException extends BaseApiException {
    public RelatedProductInactiveException() {
        super("error.product.related.inactive", HttpStatus.BAD_REQUEST);
    }
}
