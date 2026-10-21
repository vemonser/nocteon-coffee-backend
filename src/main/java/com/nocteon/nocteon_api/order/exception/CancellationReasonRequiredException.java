package com.nocteon.nocteon_api.order.exception;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

/**
 * A cancellation without a reason.
 *
 * <p>A cancellation cannot be reopened, and "why did we cancel this?" is a
 * question the team cannot answer a week later without the reason.
 */
public class CancellationReasonRequiredException extends BaseApiException {

        public CancellationReasonRequiredException() {
                super("error.order.cancellationReasonRequired", HttpStatus.BAD_REQUEST);
        }
}
