package com.nocteon.nocteon_api.common.exception.product;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

/**
 * Raised when a live (non-soft-deleted) entity already has a translation with the same
 * (language, name) as the one being created or updated. Mapped to HTTP 409 with the
 * localized {@code error.duplicate.translation} message.
 */
public class DuplicateTranslationException extends BaseApiException {
    public DuplicateTranslationException() {
        super("error.duplicate.translation", HttpStatus.CONFLICT);
    }
}