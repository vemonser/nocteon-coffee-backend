package com.nocteon.nocteon_api.notifications.stream;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.nocteon.nocteon_api.notifications.dto.NotificationResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * In-memory registry of the dashboard's server-sent-event subscribers.
 *
 * <p>This deployment is a single instance, so a process-local registry is enough: the
 * notification rows are still the source of truth, and the stream is only a delivery
 * shortcut that saves the bell a 60-second poll. The client keeps polling, so a
 * dropped emitter costs latency, never correctness.
 *
 * <p>Threading: every mutation goes through a {@link ConcurrentHashMap} of
 * {@link ConcurrentHashMap#newKeySet()} sets, and each emitter's writes are
 * serialized on the emitter itself, because a fan-out (notification thread) and a
 * heartbeat (scheduler thread) can target the same emitter at the same time. A send
 * that throws is treated as a dead client and removed immediately, so no registry
 * entry outlives its connection.
 *
 * <p>Publishing is deferred to after-commit whenever a transaction is active. The
 * notification row is inserted inside the fan-out transaction; pushing before that
 * commit would announce a row that a rollback then removes.
 */
@Component
@Slf4j
public class NotificationStreamRegistry {

    /** event: unread-count, data: the number itself (same value as GET /unread-count). */
    public static final String EVENT_UNREAD_COUNT = "unread-count";
    /** event: notification, data: the same DTO the list endpoint returns. */
    public static final String EVENT_NOTIFICATION = "notification";
    /** event: ready, data: {"userId":n} — sent once on subscribe so a client can leave backoff. */
    public static final String EVENT_READY = "ready";
    /** event: heartbeat, data: {"timestamp":"..."} — keeps proxies from cutting an idle stream. */
    public static final String EVENT_HEARTBEAT = "heartbeat";

    private final Map<Long, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final long timeoutMs;

    public NotificationStreamRegistry(
            @Value("${nocteon.notifications.stream.timeout-ms:1800000}") long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    /**
     * Registers one emitter for the user and hands it to Spring MVC. Completion,
     * timeout and error all unregister the same emitter, so a browser that closes a
     * tab, a proxy that times out the connection and a write that fails all end in
     * the same removal.
     */
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(timeoutMs);
        subscribers.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(emitter);

        Runnable unregister = () -> remove(userId, emitter);
        emitter.onCompletion(unregister);
        emitter.onTimeout(() -> {
            unregister.run();
            emitter.complete();
        });
        emitter.onError(error -> unregister.run());

        // A client that just opened the stream should not have to wait for the first
        // heartbeat to know it is live (and to reset its reconnect backoff).
        send(emitter, userId, EVENT_READY, Map.of("userId", userId));
        log.debug("Notification stream subscriber added for user {} ({} total)",
                userId, subscriberCount(userId));
        return emitter;
    }

    /** False when nobody is listening, so callers can skip the unread-count query. */
    public boolean hasSubscribers(Long userId) {
        Set<SseEmitter> emitters = subscribers.get(userId);
        return emitters != null && !emitters.isEmpty();
    }

    public int subscriberCount(Long userId) {
        Set<SseEmitter> emitters = subscribers.get(userId);
        return emitters == null ? 0 : emitters.size();
    }

    /** Total live emitters, across users. Used by tests and diagnostics. */
    public int totalSubscribers() {
        return subscribers.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * Pushes a newly created notification and the resulting unread count. Called from
     * the single creation path, after the row exists.
     */
    public void publishNotification(Long userId, NotificationResponse notification, long unreadCount) {
        runAfterCommit(() -> {
            emit(userId, EVENT_NOTIFICATION, notification);
            emit(userId, EVENT_UNREAD_COUNT, unreadCount);
        });
    }

    /**
     * Pushes only the count, for operations that change it without creating a row
     * (mark read / unread, read-all, delete). Keeps a second tab's badge honest.
     */
    public void publishUnreadCount(Long userId, long unreadCount) {
        runAfterCommit(() -> emit(userId, EVENT_UNREAD_COUNT, unreadCount));
    }

    /**
     * Heartbeat so reverse proxies do not treat an idle stream as dead. The interval
     * is configurable because a 25-second wait is not testable in a unit test.
     */
    @Scheduled(fixedDelayString = "${nocteon.notifications.stream.heartbeat-ms:25000}")
    public void heartbeat() {
        if (subscribers.isEmpty()) return;
        String timestamp = Instant.now().toString();
        for (Long userId : List.copyOf(subscribers.keySet())) {
            emit(userId, EVENT_HEARTBEAT, Map.of("timestamp", timestamp));
        }
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void emit(Long userId, String event, Object data) {
        Set<SseEmitter> emitters = subscribers.get(userId);
        if (emitters == null || emitters.isEmpty()) return;
        for (SseEmitter emitter : List.copyOf(emitters)) {
            send(emitter, userId, event, data);
        }
    }

    private void send(SseEmitter emitter, Long userId, String event, Object data) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(event).data(data));
            }
        } catch (IOException | IllegalStateException ex) {
            // A closed socket or an already-completed emitter: not an application
            // error, just a client that is gone. Drop it so the next fan-out does not
            // pay for it again.
            log.debug("Dropping notification stream subscriber for user {}: {}", userId, ex.getMessage());
            remove(userId, emitter);
            try {
                emitter.completeWithError(ex);
            } catch (RuntimeException ignored) {
                // Already completed by the container; nothing to release.
            }
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        subscribers.computeIfPresent(userId, (key, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
