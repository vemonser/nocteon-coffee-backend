package com.nocteon.nocteon_api.common.exception.notFound;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class BrewingMethodNotFoundException
        extends BaseApiException {
    public BrewingMethodNotFoundException() {
        super("error.brewingMethod.notFound", HttpStatus.NOT_FOUND);
    }
}