package com.nocteon.nocteon_api.shippingZone.exception;

public class CityAlreadyAssignedException extends RuntimeException {
    public CityAlreadyAssignedException(String city) {
        super("shipping.cityAlreadyAssigned");
        this.city = city;
    }

    private final String city;

    public String getCity() {
        return city;
    }

}