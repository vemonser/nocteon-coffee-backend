package com.nocteon.nocteon_api.promoCode.exceptions;

public class PromoCodeAlreadyExistsException extends RuntimeException {
    public PromoCodeAlreadyExistsException() {
        super("promo.alreadyExists");
    }
}