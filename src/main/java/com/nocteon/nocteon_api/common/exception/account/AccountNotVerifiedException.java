package com.nocteon.nocteon_api.common.exception.account;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class AccountNotVerifiedException extends BaseApiException {
    public AccountNotVerifiedException() {
        super("error.account.notVerified", HttpStatus.FORBIDDEN);
    }
}