package com.nocteon.nocteon_api.common.exception;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends BaseApiException {
    public RateLimitExceededException() {
        super("error.rateLimit.exceeded", HttpStatus.TOO_MANY_REQUESTS);
    }
}