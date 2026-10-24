package com.nocteon.nocteon_api.auth.exception;
import org.springframework.http.HttpStatus;
import com.nocteon.nocteon_api.common.exception.BaseApiException;
import java.util.List;
public class UnknownPermissionException extends BaseApiException {
    private final List<String> unknown;
    public UnknownPermissionException(List<String> unknown) {
        super("error.unknown.permission", HttpStatus.UNPROCESSABLE_ENTITY);
        this.unknown = unknown;
    }
    public List<String> getUnknown() { return unknown; }
}
