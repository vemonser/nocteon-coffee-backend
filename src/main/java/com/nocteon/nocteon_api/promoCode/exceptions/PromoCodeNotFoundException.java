package com.nocteon.nocteon_api.promoCode.exceptions;

public class PromoCodeNotFoundException extends RuntimeException {
    public PromoCodeNotFoundException() {
        super("promo.notFound");
    }
}