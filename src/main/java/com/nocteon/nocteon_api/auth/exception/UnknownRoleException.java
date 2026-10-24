package com.nocteon.nocteon_api.auth.exception;

import org.springframework.http.HttpStatus;
import com.nocteon.nocteon_api.common.exception.BaseApiException;
import java.util.List;

/** Raised when the role specified in the request body is not a valid role. */
public class UnknownRoleException extends BaseApiException {

    private final List<String> validRoles;

    public UnknownRoleException(List<String> validRoles) {
        super("error.unknown.role", HttpStatus.UNPROCESSABLE_ENTITY);
        this.validRoles = validRoles;
    }

    public List<String> getValidRoles() {
        return validRoles;
    }
}
