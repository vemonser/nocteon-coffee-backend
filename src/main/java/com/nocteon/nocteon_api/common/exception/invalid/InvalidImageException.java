package com.nocteon.nocteon_api.common.exception.invalid;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

/** The uploaded file is empty or is not an image. */
public class InvalidImageException extends BaseApiException {
    public InvalidImageException() {
        super("error.image.invalid", HttpStatus.BAD_REQUEST);
    }
}
