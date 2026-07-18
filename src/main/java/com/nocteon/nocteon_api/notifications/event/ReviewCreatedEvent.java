package com.nocteon.nocteon_api.notifications.event;

public record ReviewCreatedEvent(Long reviewId, String productSlug) {
}
