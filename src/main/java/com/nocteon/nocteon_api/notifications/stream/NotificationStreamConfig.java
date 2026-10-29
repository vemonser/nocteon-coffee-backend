package com.nocteon.nocteon_api.notifications.stream;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Chooses how a notification batch leaves this process.
 *
 * <p>{@code nocteon.notifications.stream.transport=redis} (the default) is the
 * multi-instance deployment: publish on pub/sub, subscribe here, fan out to the
 * emitters this process owns. {@code =local} keeps everything in the process and is
 * the honest choice for a single instance or a test that must not need a broker.
 *
 * <p>Either way the registry owns the emitters and does the local fan-out, so
 * flipping the property changes reach, not behaviour.
 */
@Configuration
@Slf4j
public class NotificationStreamConfig {

    static final String TRANSPORT_PROPERTY = "nocteon.notifications.stream.transport";

    @Bean
    @ConditionalOnProperty(name = TRANSPORT_PROPERTY, havingValue = "local")
    public NotificationBroadcaster inMemoryNotificationBroadcaster() {
        log.info("Notification stream transport: in-process only (no cross-instance delivery)");
        return InMemoryNotificationBroadcaster.INSTANCE;
    }

    @Bean
    @ConditionalOnProperty(name = TRANSPORT_PROPERTY, havingValue = "redis", matchIfMissing = true)
    public NotificationEventCodec notificationEventCodec(ObjectMapper objectMapper) {
        return new NotificationEventCodec(objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = TRANSPORT_PROPERTY, havingValue = "redis", matchIfMissing = true)
    public RedisNotificationBroadcaster redisNotificationBroadcaster(
            RedisTemplate<String, String> redisTemplate, NotificationEventCodec codec) {
        return new RedisNotificationBroadcaster(redisTemplate, codec);
    }

    /**
     * The subscription. One channel, one listener, one dispatch thread for the life of
     * the process: no subscribe/unsubscribe churn as browsers open and close tabs, and
     * no window in which an event is published while this instance is between
     * subscriptions.
     *
     * <p>The dispatch executor is single-threaded on purpose. Spring Data's default
     * task executor starts a thread per message, which would let two batches for the
     * same user be written out of order; Redis delivers in publish order, and one
     * thread carries that order to the emitters. A wedged client can hold up the
     * others on this instance until the heartbeat drops it — acceptable for a handful
     * of dashboard streams, and cheaper than a stale unread count.
     *
     * <p>If Redis is unreachable the container logs its own reconnect attempts and
     * retries on its recovery interval; the application keeps serving requests and
     * keeps its local streams alive meanwhile.
     */
    @Bean
    @ConditionalOnProperty(name = TRANSPORT_PROPERTY, havingValue = "redis", matchIfMissing = true)
    public RedisMessageListenerContainer notificationStreamListenerContainer(
            RedisConnectionFactory connectionFactory,
            NotificationEventCodec codec,
            RedisNotificationBroadcaster broadcaster,
            NotificationStreamRegistry registry) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(singleThread("notification-stream-dispatch"));
        container.setSubscriptionExecutor(singleThread("notification-stream-subscribe"));
        container.addMessageListener(
                new RedisNotificationStreamSubscriber(codec, registry, broadcaster.origin()),
                new ChannelTopic(RedisNotificationBroadcaster.CHANNEL));
        return container;
    }

    private static Executor singleThread(String name) {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        });
    }
}
