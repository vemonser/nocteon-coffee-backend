package com.nocteon.nocteon_api.notifications.stream;

import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * The wire form of a {@link NotificationEvent} on the Redis channel.
 *
 * <p>One JSON document per batch, produced by the application's own
 * {@code ObjectMapper} so that whatever it does with a {@code NotificationResponse}
 * (java.time as ISO strings, the params map as-is) it does once and every instance
 * renders the identical payload. Frame {@code data} is decoded as a generic map or
 * scalar on the receiving side and re-serialized by the SSE converter there, which
 * writes the same bytes for a map/string/number as it did for the DTO.
 *
 * <p>Encoding and decoding both fail quiet: a broadcast that cannot be serialized is
 * dropped (logged at debug), and a body this class cannot parse is ignored rather
 * than thrown back into the listener container, where it would be retried and logged
 * once per message.
 */
@Slf4j
public class NotificationEventCodec {

    private final ObjectMapper objectMapper;

    public NotificationEventCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** @return the payload, or null when the batch cannot be serialized */
    public String encode(NotificationEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (RuntimeException ex) {
            log.debug("Dropping an unserializable notification batch for user {}: {}",
                    event.userId(), ex.toString());
            return null;
        }
    }

    /** @return the event, or null when the body is not a payload this build understands */
    public NotificationEvent decode(byte[] body) {
        if (body == null || body.length == 0) return null;
        try {
            NotificationEvent event =
                    objectMapper.readValue(new String(body, StandardCharsets.UTF_8), NotificationEvent.class);
            // Unknown properties are tolerated on purpose (a newer instance may add
            // fields), but a body without an origin or without frames cannot be
            // delivered and cannot be de-duplicated: treat it as foreign.
            if (event.origin() == null || event.frames() == null) return null;
            return event;
        } catch (RuntimeException ex) {
            // Another version wrote something we do not understand (or a foreign
            // publisher used our channel). Ignoring it is the whole recovery plan.
            log.debug("Ignoring an unreadable notification stream message: {}", ex.toString());
            return null;
        }
    }
}
