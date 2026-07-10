package com.nocteon.nocteon_api.mail.exception;

public class BrevoApiException extends RuntimeException {
    public BrevoApiException(String message) { super(message); }
    public BrevoApiException(String message, Throwable cause) { super(message, cause); }
}