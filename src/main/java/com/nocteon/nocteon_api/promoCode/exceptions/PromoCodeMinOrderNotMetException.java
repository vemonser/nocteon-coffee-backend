package com.nocteon.nocteon_api.promoCode.exceptions;

public class PromoCodeMinOrderNotMetException extends RuntimeException {
    public PromoCodeMinOrderNotMetException() {
        super("promo.minOrderNotMet");
    }
}