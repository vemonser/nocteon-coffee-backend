package com.nocteon.nocteon_api.notifications.event;

public record ContactCreatedEvent(Long messageId, String subject, String senderName) {}
