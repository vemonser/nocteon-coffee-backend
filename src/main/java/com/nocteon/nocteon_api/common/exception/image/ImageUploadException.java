package com.nocteon.nocteon_api.common.exception.image;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class ImageUploadException extends BaseApiException {
    public ImageUploadException() {
        super("error.image.uploadFailed", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}