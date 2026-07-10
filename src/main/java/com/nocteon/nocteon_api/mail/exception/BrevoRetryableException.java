package com.nocteon.nocteon_api.mail.exception;

public class BrevoRetryableException extends BrevoApiException {
    public BrevoRetryableException(String message) {
        super(message);
    }
}