package com.nocteon.nocteon_api.common.exception.invalid;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class EntityInUseException extends BaseApiException {
    public EntityInUseException() {
        super("error.delete.referenced", HttpStatus.CONFLICT);
    }
}
