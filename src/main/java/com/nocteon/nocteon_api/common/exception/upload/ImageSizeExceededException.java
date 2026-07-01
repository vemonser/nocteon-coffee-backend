package com.nocteon.nocteon_api.common.exception.upload;

public class ImageSizeExceededException extends RuntimeException {

    public ImageSizeExceededException() {
        super("error.image.invalid");
    }
    
}
