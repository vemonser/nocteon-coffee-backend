package com.nocteon.nocteon_api.auth.exception;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class AccountDisabledException extends BaseApiException {
    public AccountDisabledException() {
        super("error.auth.account.disabled", HttpStatus.FORBIDDEN);
    }
}
