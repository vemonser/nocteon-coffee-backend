package com.nocteon.nocteon_api.notifications.event;

public record UserRegisteredEvent(Long userId, String fullName) {}
