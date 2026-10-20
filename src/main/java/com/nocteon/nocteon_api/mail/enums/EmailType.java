package com.nocteon.nocteon_api.mail.enums;

public enum EmailType {

    ORDER_CONFIRMATION, PAYMENT_SUCCESS, ABANDONED_CART, ADMIN_BROADCAST, ORDER_SHIPPED,
    VERIFICATION, PASSWORD_RESET,
    /** The customer is told that the status of their order changed. */
    ORDER_STATUS_CHANGED

}