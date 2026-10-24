package com.nocteon.nocteon_api.auth.exception;
import org.springframework.http.HttpStatus;
import com.nocteon.nocteon_api.common.exception.BaseApiException;
public class RoleNotFoundException extends BaseApiException {
    public RoleNotFoundException() { super("error.role.not.found", HttpStatus.NOT_FOUND); }
}
