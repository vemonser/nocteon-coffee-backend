package com.nocteon.nocteon_api.common.exception.notFound;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class ProcessingMethodNotFoundException extends BaseApiException {
    public ProcessingMethodNotFoundException() {
        super("error.processingMethod.notFound", HttpStatus.NOT_FOUND);
    
}
}