package com.nocteon.nocteon_api.notifications.event;

import java.math.BigDecimal;

public record PaymentSucceededEvent(Long orderId, BigDecimal amount) {}
