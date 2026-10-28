package com.nocteon.nocteon_api.common.exception.upload;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

/** The uploaded image is larger than the 5 MB limit. */
public class ImageSizeExceededException extends BaseApiException {

    public ImageSizeExceededException() {
        super("error.image.size.exceeded", HttpStatus.BAD_REQUEST);
    }
}
