package com.nocteon.nocteon_api.common.exception.product;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class OptionInUseException extends BaseApiException {
    private final int variantCount;

    public OptionInUseException(int variantCount) {
        super("error.option.in.use", HttpStatus.CONFLICT);
        this.variantCount = variantCount;
    }

    public int getVariantCount() {
        return variantCount;
    }
}