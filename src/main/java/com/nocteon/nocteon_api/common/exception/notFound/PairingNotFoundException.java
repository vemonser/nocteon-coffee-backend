package com.nocteon.nocteon_api.common.exception.notFound;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class PairingNotFoundException
        extends BaseApiException {
    public PairingNotFoundException() {
        super("error.pairing.notFound", HttpStatus.NOT_FOUND);
    }
}