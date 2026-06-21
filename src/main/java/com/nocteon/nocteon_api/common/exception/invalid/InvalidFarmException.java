package com.nocteon.nocteon_api.common.exception.invalid;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class InvalidFarmException extends BaseApiException {
    public InvalidFarmException() {
        super("error.farm.invalid", HttpStatus.BAD_REQUEST);
    }
}