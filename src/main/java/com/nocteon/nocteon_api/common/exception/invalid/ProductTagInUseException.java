package com.nocteon.nocteon_api.common.exception.invalid;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

/**
 * A tag still attached to products cannot be removed. The count includes trashed products: the
 * link outlives a product's soft delete, so deleting the tag would leave a restored product
 * without one.
 */
public class ProductTagInUseException extends BaseApiException {
    private final long productCount;

    public ProductTagInUseException(long productCount) {
        super("error.productTag.inUse", HttpStatus.CONFLICT, String.valueOf(productCount));
        this.productCount = productCount;
    }

    public long getProductCount() {
        return productCount;
    }
}
