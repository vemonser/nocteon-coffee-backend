package com.nocteon.nocteon_api.common.exception.product;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class DuplicateReviewException extends BaseApiException {
    public DuplicateReviewException() {
        super("error.review.duplicate", HttpStatus.CONFLICT);
    }
}