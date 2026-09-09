package com.nocteon.nocteon_api.common.exception.notFound;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class ProductOptionValueNotFoundException  extends BaseApiException {
    public ProductOptionValueNotFoundException () {
        super("error.product.optionValueNotFound", HttpStatus.NOT_FOUND);
    }
}