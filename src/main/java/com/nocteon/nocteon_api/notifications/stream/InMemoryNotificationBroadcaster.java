package com.nocteon.nocteon_api.notifications.stream;

import java.util.List;

/**
 * The single-instance transport: nothing to publish, because the registry's local
 * fan-out is the entire delivery path.
 *
 * <p>Selected with {@code nocteon.notifications.stream.transport=local} (a
 * single-instance deployment, a test that must not need a broker). It is deliberately
 * not a queue or an event bus: two instances on this transport do not see each
 * other's events, which is exactly the pre-existing single-instance behaviour.
 */
public final class InMemoryNotificationBroadcaster implements NotificationBroadcaster {

    public static final InMemoryNotificationBroadcaster INSTANCE = new InMemoryNotificationBroadcaster();

    private InMemoryNotificationBroadcaster() {
    }

    @Override
    public void broadcast(Long userId, List<NotificationFrame> frames) {
        // Already delivered to every local emitter by NotificationStreamRegistry.
    }

    @Override
    public boolean isCrossInstance() {
        return false;
    }
}
