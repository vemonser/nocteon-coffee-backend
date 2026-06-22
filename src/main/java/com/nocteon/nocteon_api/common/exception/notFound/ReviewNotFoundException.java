package com.nocteon.nocteon_api.common.exception.notFound;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class ReviewNotFoundException extends BaseApiException {
    public ReviewNotFoundException() {
        super("error.review.notFound", HttpStatus.NOT_FOUND);
    }
}