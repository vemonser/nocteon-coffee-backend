package com.nocteon.nocteon_api.common.exception.product;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class InsufficientStockException extends BaseApiException {

    private final int availableQuantity;

    public InsufficientStockException(int availableQuantity) {
        super("error.product.insufficientStock", HttpStatus.CONFLICT);
        this.availableQuantity = availableQuantity;
    }

    @Override
    public Object[] getMessageArgs() {
        return new Object[]{availableQuantity};
    }
}