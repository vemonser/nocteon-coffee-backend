package com.nocteon.nocteon_api.promoCode.exceptions;

public class PromoCodeCategoryRequiredException extends RuntimeException {
    public PromoCodeCategoryRequiredException() {
        super("promo.categoryRequired");
    }
}