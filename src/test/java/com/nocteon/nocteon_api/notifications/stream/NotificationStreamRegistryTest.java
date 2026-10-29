package com.nocteon.nocteon_api.notifications.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.nocteon.nocteon_api.notifications.dto.NotificationResponse;

/**
 * Registry semantics that do not need a servlet container: who is registered, that a
 * dead emitter is dropped instead of being retried forever, and that the map survives
 * concurrent subscribe/publish/remove.
 *
 * <p>The wire format itself (event names, payloads, heartbeat timing) is asserted over
 * real HTTP in {@code NotificationStreamIntegrationTest}, because a bare
 * {@link SseEmitter} buffers sends until a handler exists and cannot be inspected.
 */
class NotificationStreamRegistryTest {

    private static final long USER_A = 700_001L;
    private static final long USER_B = 700_002L;

    private final NotificationStreamRegistry registry = new NotificationStreamRegistry(60_000L);

    private static NotificationResponse notification(long id) {
        return NotificationResponse.builder()
                .id(id)
                .type("test.stream")
                .entityType("test")
                .entityId(id)
                .build();
    }

    @Test
    void registersEachEmitterAndReportsCountsPerUser() {
        registry.subscribe(USER_A);
        registry.subscribe(USER_A);
        registry.subscribe(USER_B);

        assertThat(registry.subscriberCount(USER_A)).isEqualTo(2);
        assertThat(registry.subscriberCount(USER_B)).isEqualTo(1);
        assertThat(registry.totalSubscribers()).isEqualTo(3);
        assertThat(registry.hasSubscribers(USER_A)).isTrue();
        assertThat(registry.hasSubscribers(999L)).isFalse();
    }

    @Test
    void dropsACompletedEmitterOnTheNextPublish() {
        SseEmitter emitter = registry.subscribe(USER_A);
        assertThat(registry.subscriberCount(USER_A)).isEqualTo(1);

        // A closed tab: the container has completed the emitter, so the next write
        // cannot succeed. The registry must forget it rather than keep writing.
        emitter.complete();
        registry.publishNotification(USER_A, notification(1L), 1L);

        assertThat(registry.subscriberCount(USER_A)).isZero();
        assertThat(registry.hasSubscribers(USER_A)).isFalse();
    }

    @Test
    void removingOneUserLeavesTheOtherSubscribed() {
        SseEmitter first = registry.subscribe(USER_A);
        registry.subscribe(USER_B);

        first.complete();
        registry.publishUnreadCount(USER_A, 3L);

        assertThat(registry.subscriberCount(USER_A)).isZero();
        assertThat(registry.subscriberCount(USER_B)).isEqualTo(1);
    }

    @Test
    void publishingToNobodyIsANoOp() {
        registry.publishNotification(USER_A, notification(1L), 1L);
        registry.publishUnreadCount(USER_A, 1L);
        registry.heartbeat();

        assertThat(registry.totalSubscribers()).isZero();
    }

    @Test
    void survivesConcurrentSubscribeAndPublish() throws Exception {
        int threads = 4;
        int perThread = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<SseEmitter> emitters = new ArrayList<>();

        try {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        SseEmitter emitter = registry.subscribe(USER_A);
                        synchronized (emitters) {
                            emitters.add(emitter);
                        }
                        registry.publishNotification(USER_A, notification(i), i);
                        registry.heartbeat();
                    }
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(registry.subscriberCount(USER_A)).isEqualTo(threads * perThread);

        emitters.forEach(SseEmitter::complete);
        registry.publishUnreadCount(USER_A, 0L);

        assertThat(registry.subscriberCount(USER_A)).isZero();
        assertThat(registry.totalSubscribers()).isZero();
    }
}
