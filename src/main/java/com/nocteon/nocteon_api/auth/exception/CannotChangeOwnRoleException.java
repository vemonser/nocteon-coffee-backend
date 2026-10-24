package com.nocteon.nocteon_api.auth.exception;

import org.springframework.http.HttpStatus;
import com.nocteon.nocteon_api.common.exception.BaseApiException;

/** Raised when a caller attempts to change their own role. */
public class CannotChangeOwnRoleException extends BaseApiException {

    public CannotChangeOwnRoleException() {
        super("error.cannot.change.own.role", HttpStatus.FORBIDDEN);
    }
}