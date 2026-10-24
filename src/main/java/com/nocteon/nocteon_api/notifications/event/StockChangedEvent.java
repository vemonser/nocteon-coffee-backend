package com.nocteon.nocteon_api.notifications.event;

public record StockChangedEvent(Long variantId, String productName, String variantLabel, int oldStock, int newStock, int threshold) {}
