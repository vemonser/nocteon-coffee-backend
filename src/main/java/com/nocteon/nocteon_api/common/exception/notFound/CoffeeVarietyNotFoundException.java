package com.nocteon.nocteon_api.common.exception.notFound;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class CoffeeVarietyNotFoundException extends BaseApiException {

    public CoffeeVarietyNotFoundException() {
        super("error.coffeeVariety.notFound", HttpStatus.NOT_FOUND);

    }

}