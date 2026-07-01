package com.nocteon.nocteon_api.common.exception.upload;

public class VideoSizeExceededException extends RuntimeException {

    public VideoSizeExceededException() {
        super("error.video.invalid");
    }
}
