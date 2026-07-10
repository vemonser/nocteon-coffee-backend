package com.nocteon.nocteon_api.promoCode.exceptions;

public class PromoCodeExhaustedException extends RuntimeException {
    public PromoCodeExhaustedException() {
        super("promo.exhausted");
    }
}