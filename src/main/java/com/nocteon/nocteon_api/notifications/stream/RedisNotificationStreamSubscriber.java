package com.nocteon.nocteon_api.notifications.stream;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Receives every batch published on the channel and hands the ones that belong to
 * another instance to this instance's emitters.
 *
 * <p>The echo of this instance's own publish is dropped by {@code origin}. That check
 * is the difference between "the bell rings once" and "the bell rings twice": the
 * registry emits locally, synchronously, before publishing, and Redis sends the
 * message straight back to this subscriber.
 *
 * <p>Nothing here touches the database: the payload is what the creating instance
 * already committed, so a delivery can never announce a row that does not exist.
 */
@Slf4j
@RequiredArgsConstructor
public class RedisNotificationStreamSubscriber implements MessageListener {

    private final NotificationEventCodec codec;
    private final NotificationStreamRegistry registry;
    /** This instance's id, as stamped by {@link RedisNotificationBroadcaster#origin()}. */
    private final String origin;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        NotificationEvent event = codec.decode(message.getBody());
        if (event == null) return;
        if (origin.equals(event.origin())) {
            // Our own publish: already delivered locally. Emitting it again would
            // duplicate the notification and the count on every stream we own.
            return;
        }
        try {
            registry.deliverLocal(event.userId(), event.frames());
        } catch (RuntimeException ex) {
            // A single bad batch must not take the subscription down: the container
            // would log this per message and keep reconnecting.
            log.debug("Dropped a cross-instance notification batch for user {}: {}",
                    event.userId(), ex.toString());
        }
    }
}
