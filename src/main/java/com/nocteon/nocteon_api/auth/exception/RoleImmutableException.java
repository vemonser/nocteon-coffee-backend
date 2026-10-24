package com.nocteon.nocteon_api.auth.exception;
import org.springframework.http.HttpStatus;
import com.nocteon.nocteon_api.common.exception.BaseApiException;
public class RoleImmutableException extends BaseApiException {
    public RoleImmutableException() { super("error.role.immutable", HttpStatus.CONFLICT); }
}
