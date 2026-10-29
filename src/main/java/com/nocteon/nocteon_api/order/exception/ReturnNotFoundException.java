package com.nocteon.nocteon_api.order.exception;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

/**
 * A return id that does not exist, or does not belong to the order in the URL.
 *
 * <p>A return of ANOTHER order is a 404 and not a 400 on purpose: the caller has
 * no business knowing that the id exists somewhere else.
 */
public class ReturnNotFoundException extends BaseApiException {

    public ReturnNotFoundException() {
        super("error.return.notFound", HttpStatus.NOT_FOUND);
    }
}
