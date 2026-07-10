package com.nocteon.nocteon_api.promoCode.exceptions;

public class PromoCodeAlreadyUsedException extends RuntimeException {
    public PromoCodeAlreadyUsedException() {
        super("promo.alreadyUsed");
    }
}