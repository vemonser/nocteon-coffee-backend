package com.nocteon.nocteon_api.notifications.event;

import java.math.BigDecimal;

public record OrderCreatedEvent(Long orderId, BigDecimal totalAmount) {}
