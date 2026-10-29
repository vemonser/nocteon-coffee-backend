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
 * Registry of the dashboard's server-sent-event subscribers, for this process only.
 *
 * <p><b>Ownership.</b> The {@code Map<userId, Set<SseEmitter>>} stays here and here
 * only: a connection lives in the process that accepted it, so this class is the only
 * code that may write to an emitter. Other instances reach those emitters through
 * {@link NotificationBroadcaster} and {@link #deliverLocal}, never by owning state.
 *
 * <p><b>Delivery.</b> A publish fans out locally first (synchronously, exactly as
 * before) and then, best effort, asks the broadcaster to announce the same batch to
 * the other instances. Two consequences worth knowing:
 * <ul>
 *   <li>a single instance behaves exactly as it did when this was the whole story —
 *       same events, same payloads, same order, and a broker outage costs nothing;</li>
 *   <li>an emitter on another instance receives the batch through the subscription that
 *       {@code RedisNotificationStreamSubscriber} drives, which is why the event carries
 *       the publisher's id and the publisher's own echo is dropped. Otherwise every
 *       local emitter would see each event twice.</li>
 * </ul>
 *
 * <p>The notification row is still the source of truth and the client still polls, so a
 * dropped batch costs latency, never correctness.
 *
 * <p>Threading: every mutation goes through a {@link ConcurrentHashMap} of
 * {@link ConcurrentHashMap#newKeySet()} sets, and each emitter's writes are
 * serialized on the emitter itself, because a fan-out (fan-out thread, request thread
 * or the pub/sub dispatch thread) and a heartbeat (scheduler thread) can target the
 * same emitter at the same time. A send that throws is treated as a dead client and
 * removed immediately, so no registry entry outlives its connection.
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
    private final NotificationBroadcaster broadcaster;

    public NotificationStreamRegistry(
            @Value("${nocteon.notifications.stream.timeout-ms:1800000}") long timeoutMs,
            NotificationBroadcaster broadcaster) {
        this.timeoutMs = timeoutMs;
        this.broadcaster = broadcaster;
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

    /** Emitters registered in this process. Never widened to other instances. */
    public int subscriberCount(Long userId) {
        Set<SseEmitter> emitters = subscribers.get(userId);
        return emitters == null ? 0 : emitters.size();
    }

    /** Total live emitters, across users. Used by tests and diagnostics. */
    public int totalSubscribers() {
        return subscribers.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * True when somebody might be listening to this user: an emitter here, or — with
     * the cross-instance transport on — an emitter this process cannot see. Callers use
     * it to skip the unread-count query for a REST-only client, not to decide delivery.
     */
    public boolean mayHaveSubscribers(Long userId) {
        return hasSubscribers(userId) || broadcaster.isCrossInstance();
    }

    /** Emitters registered in this process only; the strict, local answer. */
    public boolean hasSubscribers(Long userId) {
        Set<SseEmitter> emitters = subscribers.get(userId);
        return emitters != null && !emitters.isEmpty();
    }

    /**
     * Pushes a newly created notification and the resulting unread count. Called from
     * the single creation path, after the row exists.
     */
    public void publishNotification(Long userId, NotificationResponse notification, long unreadCount) {
        announceAfterCommit(userId, List.of(
                new NotificationFrame(EVENT_NOTIFICATION, notification),
                new NotificationFrame(EVENT_UNREAD_COUNT, unreadCount)));
    }

    /**
     * Pushes only the count, for operations that change it without creating a row
     * (mark read / unread, read-all, delete). Keeps a second tab's badge honest — and,
     * through the broadcaster, a tab attached to another instance.
     */
    public void publishUnreadCount(Long userId, long unreadCount) {
        announceAfterCommit(userId, List.of(new NotificationFrame(EVENT_UNREAD_COUNT, unreadCount)));
    }

    /**
     * Emits a batch to the emitters registered here. The only entry point for a batch
     * that came from another instance, and the local half of every publish.
     *
     * <p>Frames are emitted in list order on the calling thread, which is what keeps
     * {@code notification} ahead of its own {@code unread-count}.
     */
    public void deliverLocal(Long userId, List<NotificationFrame> frames) {
        for (NotificationFrame frame : frames) {
            emit(userId, frame.name(), frame.data());
        }
    }

    /**
     * Heartbeat so reverse proxies do not treat an idle stream as dead. Deliberately
     * not broadcast: every instance keeps its own connections warm, and a heartbeat
     * that travelled through Redis would be pure noise. The interval is configurable
     * because a 25-second wait is not testable in a unit test.
     */
    @Scheduled(fixedDelayString = "${nocteon.notifications.stream.heartbeat-ms:25000}")
    public void heartbeat() {
        if (subscribers.isEmpty()) return;
        String timestamp = Instant.now().toString();
        for (Long userId : List.copyOf(subscribers.keySet())) {
            emit(userId, EVENT_HEARTBEAT, Map.of("timestamp", timestamp));
        }
    }

    private void announceAfterCommit(Long userId, List<NotificationFrame> frames) {
        List<NotificationFrame> batch = List.copyOf(frames);
        runAfterCommit(() -> {
            deliverLocal(userId, batch);
            broadcaster.broadcast(userId, batch);
        });
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

    /**
     * One write to one emitter. {@code protected} so a test can observe the frames
     * without a servlet container: a bare {@link SseEmitter} buffers sends until a
     * handler exists, so the only other way to see them is real HTTP.
     */
    protected void send(SseEmitter emitter, Long userId, String event, Object data) {
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
