package com.nocteon.nocteon_api.notifications.stream;

import java.util.List;

/**
 * How a notification frame batch leaves this process so that it can reach an emitter
 * that is attached to a different instance.
 *
 * <p>The registry owns the emitters and always fans out locally first; a broadcaster
 * only adds the cross-instance leg. That split is what lets a Redis outage degrade to
 * single-instance behaviour instead of an outage of the feature: the local emitter
 * still fires, and only the other instances wait for the client's 60-second poll.
 *
 * <p>Implementations must be cheap, non-throwing and non-blocking: {@link #broadcast}
 * runs after the creating transaction committed, so nothing it does can (or should)
 * change the outcome of that transaction, and a dead broker must never turn a
 * mark-as-read request into a slow one.
 */
public interface NotificationBroadcaster {

    /**
     * Publishes one batch for one user. Never throws; a batch that cannot be
     * published is dropped, which costs the other instances at most one poll
     * interval.
     *
     * @param userId the recipient of the batch
     * @param frames the events to emit, in delivery order
     */
    void broadcast(Long userId, List<NotificationFrame> frames);

    /**
     * True when a live emitter may exist on another instance. Callers combine it with
     * the local {@code hasSubscribers} check to decide whether computing an unread
     * count is worth a query at all.
     */
    boolean isCrossInstance();

    /** Single-instance transport: local fan-out is the whole delivery. */
    static NotificationBroadcaster localOnly() {
        return InMemoryNotificationBroadcaster.INSTANCE;
    }
}
