package com.nocteon.nocteon_api.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public abstract class BaseApiException extends RuntimeException {

    private final String messageKey;
    private final HttpStatus status;

    protected BaseApiException(String messageKey, HttpStatus status) {
        super(messageKey);
        this.messageKey = messageKey;
        this.status = status;
    }

    public Object[] getMessageArgs() {
        return null;
    }
}