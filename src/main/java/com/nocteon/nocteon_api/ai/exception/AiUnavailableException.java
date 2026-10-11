package com.nocteon.nocteon_api.ai.exception;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

/**
 * Raised by AI endpoints that cannot degrade.
 *
 * <p>Most of the AI surface deliberately never throws this: {@code /products/{slug}/similar}
 * falls back to attribute similarity rather than failing, because a storefront product page is
 * not the right place to surface an infrastructure outage. This exists for the endpoints where
 * degrading would mean answering with something other than what was asked — notably the
 * shopping assistant, where an attribute-similarity guess in place of an answer would be a
 * fabricated answer.
 *
 * <p>503 rather than 500 because the cause is a downstream dependency being unreachable, not a
 * defect in this service; clients and monitoring should treat it as retryable.
 */
public class AiUnavailableException extends BaseApiException {

    public AiUnavailableException() {
        super("error.ai.unavailable", HttpStatus.SERVICE_UNAVAILABLE);
    }
}