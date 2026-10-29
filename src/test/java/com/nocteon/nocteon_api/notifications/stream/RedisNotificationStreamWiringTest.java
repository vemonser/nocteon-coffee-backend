package com.nocteon.nocteon_api.notifications.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * Proves the real wiring over a real broker: the application's
 * {@code RedisMessageListenerContainer} is subscribed to
 * {@link RedisNotificationBroadcaster#CHANNEL}, it understands the payload
 * {@link NotificationEventCodec} writes, and it forwards a foreign batch to this
 * instance's registry while dropping its own echo.
 *
 * <p>The registry is replaced by a mock so the assertion is about the transport — what
 * arrives, for which user, from whom — not about an emitter. Emitter behaviour is
 * covered by {@code NotificationStreamFanOutTest}, and the browser-facing wire format
 * by {@code NotificationStreamIntegrationTest}.
 *
 * <p>Uses the local Redis the application already needs to start. It is not a test of
 * Redis; it is a test that the subscription is connected to the channel the broadcaster
 * publishes on.
 */
@SpringBootTest(classes = NocteonApiApplication.class)
class RedisNotificationStreamWiringTest {

    private static final long USER = 812_345L;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private NotificationEventCodec codec;

    @Autowired
    private RedisNotificationBroadcaster broadcaster;

    @MockitoBean
    private NotificationStreamRegistry registry;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Test
    void aForeignBatchArrivesHereAndAnOwnEchoIsIgnored() {
        // "Another instance" publishing on the shared channel.
        NotificationEvent foreign = new NotificationEvent("another-instance", USER,
                List.of(new NotificationFrame(NotificationStreamRegistry.EVENT_NOTIFICATION,
                                java.util.Map.of("id", 5L)),
                        new NotificationFrame(NotificationStreamRegistry.EVENT_UNREAD_COUNT, 3L)));

        publish(foreign);

        // Asynchronous by construction: the container dispatches on its own thread.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationFrame>> frames = ArgumentCaptor.forClass(List.class);
        verify(registry, timeout(15_000).times(1)).deliverLocal(eq(USER), frames.capture());
        assertThat(frames.getValue()).extracting(NotificationFrame::name)
                .containsExactly(NotificationStreamRegistry.EVENT_NOTIFICATION,
                        NotificationStreamRegistry.EVENT_UNREAD_COUNT);

        // Redis echoes the publish back to every subscriber, this instance included.
        // A batch stamped with our own origin must not be fanned out a second time.
        reset(registry);
        publish(new NotificationEvent(broadcaster.origin(), USER,
                List.of(new NotificationFrame(NotificationStreamRegistry.EVENT_UNREAD_COUNT, 3L))));

        verify(registry, after(1_500).never()).deliverLocal(eq(USER), anyList());
    }

    @Test
    void aPayloadThisBuildCannotReadDoesNotBreakTheSubscription() {
        redisTemplate.convertAndSend(RedisNotificationBroadcaster.CHANNEL, "not json");
        redisTemplate.convertAndSend(RedisNotificationBroadcaster.CHANNEL, "{\"unexpected\":true}");

        // Still subscribed: a valid batch published after the noise arrives normally.
        long otherUser = USER + 1;
        publish(new NotificationEvent("another-instance", otherUser,
                List.of(new NotificationFrame(NotificationStreamRegistry.EVENT_UNREAD_COUNT, 1L))));

        verify(registry, timeout(15_000).times(1)).deliverLocal(eq(otherUser), anyList());
        verify(registry, never()).deliverLocal(eq(USER), anyList());
    }

    private void publish(NotificationEvent event) {
        redisTemplate.convertAndSend(RedisNotificationBroadcaster.CHANNEL, codec.encode(event));
    }
}
