package com.nocteon.nocteon_api.common.exception.email;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class EmailSendingException extends BaseApiException {
    public EmailSendingException(Throwable cause) {
        super("error.email.sendingFailed", HttpStatus.INTERNAL_SERVER_ERROR);
        if (cause != null) {
            initCause(cause);
        }
    }
}