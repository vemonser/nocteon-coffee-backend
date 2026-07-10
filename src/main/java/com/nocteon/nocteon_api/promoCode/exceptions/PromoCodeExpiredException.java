package com.nocteon.nocteon_api.promoCode.exceptions;

public class PromoCodeExpiredException extends RuntimeException {
    public PromoCodeExpiredException() {
        super("promo.expired");
    }
}