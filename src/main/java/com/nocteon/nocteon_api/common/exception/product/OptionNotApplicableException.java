package com.nocteon.nocteon_api.common.exception.product;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class OptionNotApplicableException extends BaseApiException {
    public OptionNotApplicableException() {
        super("error.option.not.applicable", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}