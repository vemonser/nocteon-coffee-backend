package com.nocteon.nocteon_api.shippingZone.exception;

public class ShippingZoneNotFoundException extends RuntimeException {
    public ShippingZoneNotFoundException() {
        super("shipping.zoneNotFound");
    }
}