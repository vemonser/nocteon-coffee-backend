package com.nocteon.nocteon_api.promoCode.exceptions;

public class PromoCodeNotApplicableException extends RuntimeException {
    public PromoCodeNotApplicableException() {
        super("promo.notApplicable");
    }
}