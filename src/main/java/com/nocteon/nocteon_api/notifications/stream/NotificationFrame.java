package com.nocteon.nocteon_api.notifications.stream;

/**
 * One server-sent event: the name the client listens for and the exact object the
 * local fan-out hands to {@code SseEmitter}.
 *
 * <p>Frames travel as an ordered list inside one {@link NotificationEvent} so that a
 * "notification then its unread count" pair can never be reordered, split or
 * half-delivered by a transport hop. This is the same pair, in the same order, that
 * the registry used to send one after the other on the publishing thread.
 *
 * @param name the SSE event name ({@code notification}, {@code unread-count}, ...)
 * @param data the DTO or scalar the client parses; never a pre-rendered string
 */
public record NotificationFrame(String name, Object data) {
}
