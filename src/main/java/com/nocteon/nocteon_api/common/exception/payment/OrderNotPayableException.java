package com.nocteon.nocteon_api.common.exception.payment;

public class OrderNotPayableException extends RuntimeException {
    public OrderNotPayableException(String message) {
        super(message);
    }
}