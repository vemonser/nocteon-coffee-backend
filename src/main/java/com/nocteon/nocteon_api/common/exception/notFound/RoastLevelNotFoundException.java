package com.nocteon.nocteon_api.common.exception.notFound;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class RoastLevelNotFoundException extends BaseApiException{
    public RoastLevelNotFoundException() {
        super("error.roastLevel.notFound", HttpStatus.NOT_FOUND);
    
}
}