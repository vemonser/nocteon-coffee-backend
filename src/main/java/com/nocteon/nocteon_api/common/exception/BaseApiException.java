package com.nocteon.nocteon_api.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public abstract class BaseApiException extends RuntimeException {

    private final String messageKey;
    private final HttpStatus status;
    /**
     * The one argument the message carries — the detail from the provider
     * that refused, for instance. Null when the message needs no argument.
     */
    private final String messageArg;

    protected BaseApiException(String messageKey, HttpStatus status) {
        this(messageKey, status, null);
    }

    protected BaseApiException(String messageKey, HttpStatus status, String messageArg) {
        super(messageKey);
        this.messageKey = messageKey;
        this.status = status;
        this.messageArg = messageArg;
    }

    public Object[] getMessageArgs() {
        return messageArg == null ? null : new Object[] { messageArg };
    }
}
