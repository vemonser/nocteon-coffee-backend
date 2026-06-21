package com.nocteon.nocteon_api.common.exception.account;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class AccountLockedException extends BaseApiException {

    private final long remainingMinutes;

    public AccountLockedException(long remainingMinutes) {
        super("error.account.locked", HttpStatus.LOCKED);
        this.remainingMinutes = remainingMinutes;
    }

    @Override
    public Object[] getMessageArgs() {
        return new Object[] { remainingMinutes };
    }
}