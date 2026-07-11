package com.nocteon.nocteon_api.auth.exception;


import com.nocteon.nocteon_api.common.exception.BaseApiException;
import org.springframework.http.HttpStatus;


public class AccountLockedException extends BaseApiException {
    public AccountLockedException() {
        super("error.auth.account.locked", HttpStatus.LOCKED);
    }
}
