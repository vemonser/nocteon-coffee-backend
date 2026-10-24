package com.nocteon.nocteon_api.auth.exception;
import org.springframework.http.HttpStatus;
import com.nocteon.nocteon_api.common.exception.BaseApiException;
public class RoleInUseException extends BaseApiException {
    public RoleInUseException() { super("error.role.in.use", HttpStatus.CONFLICT); }
}
