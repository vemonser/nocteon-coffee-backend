package com.nocteon.nocteon_api.notifications.stream;

import java.util.List;

/**
 * Everything one publish call has to announce, addressed to one user.
 *
 * <p>{@code origin} is the id of the process that created the event. Redis pub/sub
 * echoes a message back to every subscriber of the channel, including the publisher,
 * so the receiver drops its own events: the registry already delivered them locally,
 * synchronously, before publishing. Without this field every bell would ring twice.
 *
 * @param origin the publishing instance's id, not a user or a tenant
 * @param userId the recipient; the receiving instance fans out to its own emitters only
 * @param frames the events to emit, in delivery order
 */
public record NotificationEvent(String origin, long userId, List<NotificationFrame> frames) {
}
