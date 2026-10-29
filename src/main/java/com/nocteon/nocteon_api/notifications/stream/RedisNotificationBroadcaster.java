package com.nocteon.nocteon_api.notifications.stream;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.data.redis.core.RedisTemplate;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes notification batches on Redis pub/sub so that every instance can fan them
 * out to its own emitters.
 *
 * <p><b>Degrades honestly.</b> Three things make a broker problem a latency problem
 * instead of an availability one:
 * <ol>
 *   <li>the local fan-out already happened synchronously on the publishing instance
 *       (the registry does that before calling here), so a dead Redis never costs the
 *       instance that created the row its own live updates;</li>
 *   <li>the publish runs on a dedicated thread, never on the request (or after-commit
 *       callback) thread, so the {@code spring.data.redis.timeout} (5s by default)
 *       that a black-holed broker costs is paid off the request path;</li>
 *   <li>failures are swallowed and logged once per outage, with a matching line when
 *       the broker comes back, instead of one stack trace per notification.</li>
 * </ol>
 *
 * <p>Ordering: the queue is FIFO on one thread, so batches reach Redis in the order
 * they were published, and the frames inside one batch are one message. A user's
 * {@code notification} can therefore never overtake its own {@code unread-count}.
 *
 * <p>The publisher drops a batch rather than applying backpressure when the queue is
 * full (1024 batches). Dropping is safe: the row is committed and the client's poll
 * finds it, which is the same recovery as a broker outage.
 */
@Slf4j
public class RedisNotificationBroadcaster implements NotificationBroadcaster {

    /** One topic for every recipient; the payload carries the userId. See DECISIONS.md. */
    public static final String CHANNEL = "nocteon:notifications:stream";

    private static final int QUEUE_CAPACITY = 1024;

    private final RedisTemplate<String, String> redisTemplate;
    private final NotificationEventCodec codec;
    private final String origin;
    private final Executor publisher;

    /** True from the first failed publish until one succeeds again: gates the log. */
    private final AtomicBoolean degraded = new AtomicBoolean();
    /** Failed (or dropped) publishes since the outage started; reported on recovery. */
    private final AtomicLong suppressedFailures = new AtomicLong();

    /** Production wiring: its own id, the shared channel and the publish thread. */
    public RedisNotificationBroadcaster(RedisTemplate<String, String> redisTemplate, NotificationEventCodec codec) {
        this(redisTemplate, codec, UUID.randomUUID().toString(), publisherExecutor());
    }

    /** Test wiring: a fixed origin and a caller-chosen executor (a same-thread one is fine). */
    RedisNotificationBroadcaster(
            RedisTemplate<String, String> redisTemplate,
            NotificationEventCodec codec,
            String origin,
            Executor publisher) {
        this.redisTemplate = redisTemplate;
        this.codec = codec;
        this.origin = origin;
        this.publisher = publisher;
        log.info("Notification stream transport: Redis pub/sub on '{}' (instance {})", CHANNEL, origin);
    }

    /** This instance's id. A batch carrying it is its own echo and must not be re-emitted. */
    public String origin() {
        return origin;
    }

    @Override
    public void broadcast(Long userId, List<NotificationFrame> frames) {
        NotificationEvent event = new NotificationEvent(origin, userId, List.copyOf(frames));
        try {
            publisher.execute(() -> publish(event));
        } catch (RejectedExecutionException ex) {
            // Queue full (a broker that is down and a burst of notifications) or the
            // application is shutting down. Either way the row is committed.
            onFailure("publish queue full or shutting down", ex);
        } catch (RuntimeException ex) {
            // Belt and braces: this interface promises never to throw.
            onFailure("could not schedule the publish", ex);
        }
    }

    @Override
    public boolean isCrossInstance() {
        return true;
    }

    private void publish(NotificationEvent event) {
        String payload = codec.encode(event);
        if (payload == null) {
            onFailure("unserializable batch", null);
            return;
        }
        try {
            redisTemplate.convertAndSend(CHANNEL, payload);
            recovered();
        } catch (RuntimeException ex) {
            onFailure("publish failed", ex);
        }
    }

    /** One line per outage; the count that follows it is how a burst looks in the log. */
    private void onFailure(String what, RuntimeException cause) {
        long failures = suppressedFailures.incrementAndGet();
        if (degraded.compareAndSet(false, true)) {
            log.warn("Notification stream broadcast to Redis failed ({}). Local streams keep working;"
                            + " other instances recover on the client's next poll. Cause: {}",
                    what, cause == null ? "see debug log" : cause.toString());
            log.debug("First notification broadcast failure", cause);
        } else {
            log.trace("Notification broadcast still failing ({} since the outage began)", failures);
        }
    }

    private void recovered() {
        if (!degraded.getAndSet(false)) return;
        log.info("Notification stream broadcast to Redis recovered ({} failed or dropped publishes during the outage)",
                suppressedFailures.getAndSet(0));
    }

    @PreDestroy
    void shutdown() {
        if (!(publisher instanceof ThreadPoolExecutor pool)) return;
        pool.shutdown();
        try {
            if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException ex) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadPoolExecutor publisherExecutor() {
        return new ThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "notification-broadcast");
                    // Daemon: a stuck publish must not hold up the JVM's exit; the
                    // batch it is holding is best-effort by definition.
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }
}
