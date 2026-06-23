package com.nocteon.nocteon_api.common.exception.notFound;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class AddressNotFoundException extends BaseApiException {
    public AddressNotFoundException() {
        super("error.address.notFound", HttpStatus.NOT_FOUND);
    }
}