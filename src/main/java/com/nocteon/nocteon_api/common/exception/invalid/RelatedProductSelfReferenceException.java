package com.nocteon.nocteon_api.common.exception.invalid;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

/** FE-11: a product cannot point at itself as "You may also like". */
public class RelatedProductSelfReferenceException extends BaseApiException {
    public RelatedProductSelfReferenceException() {
        super("error.product.related.self", HttpStatus.BAD_REQUEST);
    }
}
