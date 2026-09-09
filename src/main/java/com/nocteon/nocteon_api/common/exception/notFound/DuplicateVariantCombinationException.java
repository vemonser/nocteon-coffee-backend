package com.nocteon.nocteon_api.common.exception.notFound;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class DuplicateVariantCombinationException extends BaseApiException {
    public DuplicateVariantCombinationException() {
        super("error.product.duplicateVariant", HttpStatus.CONFLICT);
    }
}