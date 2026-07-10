package com.nocteon.nocteon_api.mail.event;

public record PaymentSucceededEvent(Long orderId, Long paymentId) {}
