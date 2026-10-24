package com.nocteon.nocteon_api.auth.exception;

import org.springframework.http.HttpStatus;
import com.nocteon.nocteon_api.common.exception.BaseApiException;

/** Raised when a role change would leave zero admins in the system. */
public class LastAdminRequiredException extends BaseApiException {

    public LastAdminRequiredException() {
        super("error.last.admin.required", HttpStatus.CONFLICT);
    }
}