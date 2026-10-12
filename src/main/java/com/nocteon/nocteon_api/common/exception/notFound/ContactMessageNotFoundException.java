package com.nocteon.nocteon_api.common.exception.notFound;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

/**
 * A contact message id does not resolve.
 *
 * <p>Follows the existing convention: one {@code *NotFoundException} per entity in this package,
 * carrying a message key rather than a literal, so {@code GlobalExceptionHandler} can resolve it
 * through {@code MessageSource} and return it in the caller's locale.
 *
 * <p>Added because {@code ContactService} referenced a generic
 * {@code common.exception.NotFoundException} that does not exist, which left the project unable
 * to compile.
 */
public class ContactMessageNotFoundException extends BaseApiException {

    public ContactMessageNotFoundException() {
        super("error.contact.notFound", HttpStatus.NOT_FOUND);
    }
}