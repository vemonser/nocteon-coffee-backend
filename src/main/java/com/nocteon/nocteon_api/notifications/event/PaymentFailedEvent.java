package com.nocteon.nocteon_api.notifications.event;

import java.math.BigDecimal;

public record PaymentFailedEvent(Long orderId, BigDecimal amount) {}
