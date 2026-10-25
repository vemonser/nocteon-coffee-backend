package com.nocteon.nocteon_api.common.exception.invalid;

import java.util.List;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.dto.ApiFieldError;
import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class FieldValidationException extends BaseApiException {
    private final List<ApiFieldError> fieldErrors;

    public FieldValidationException(List<ApiFieldError> fieldErrors) {
        super("error.validation.failed", HttpStatus.UNPROCESSABLE_ENTITY);
        this.fieldErrors = fieldErrors;
    }

    public List<ApiFieldError> getFieldErrors() {
        return fieldErrors;
    }
}