package com.nocteon.nocteon_api.promoCode.exceptions;

public class PromoCodeInactiveException extends RuntimeException {
    public PromoCodeInactiveException() {
        super("promo.inactive");
    }
}