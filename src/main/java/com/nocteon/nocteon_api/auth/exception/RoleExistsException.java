package com.nocteon.nocteon_api.auth.exception;
import org.springframework.http.HttpStatus;
import com.nocteon.nocteon_api.common.exception.BaseApiException;
public class RoleExistsException extends BaseApiException {
    public RoleExistsException() { super("error.role.exists", HttpStatus.CONFLICT); }
}
