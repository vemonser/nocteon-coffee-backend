package com.nocteon.nocteon_api.shippingZone.exception;

public class ShippingNotAvailableException extends RuntimeException {
    public ShippingNotAvailableException() {
        super("shipping.notAvailable");
    }
}