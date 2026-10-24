package com.nocteon.nocteon_api.auth.exception;

import org.springframework.http.HttpStatus;
import com.nocteon.nocteon_api.common.exception.BaseApiException;
import java.util.List;
import com.nocteon.nocteon_api.auth.enums.Role;

/** Raised when the role specified in the request body is not a valid role. */
public class UnknownRoleException extends BaseApiException {

    private final List<Role> validRoles;

    public UnknownRoleException(List<Role> validRoles) {
        super("error.unknown.role", HttpStatus.UNPROCESSABLE_ENTITY);
        this.validRoles = validRoles;
    }

    public List<Role> getValidRoles() {
        return validRoles;
    }
}