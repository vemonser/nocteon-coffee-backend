package com.nocteon.nocteon_api.notifications.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.nocteon.nocteon_api.notifications.dto.NotificationResponse;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Multi-instance behaviour of the notification stream, with the Redis channel replaced
 * by an in-JVM channel that delivers a payload to every subscriber — including the
 * publisher, exactly like Redis pub/sub does. That echo is the whole point: it is what
 * turns a missing origin check into a doubled bell.
 *
 * <p>No broker and no servlet container: the emitters are real {@link SseEmitter}s, but
 * the registry's write is observed through the {@code send} seam instead of over HTTP
 * ({@link NotificationStreamIntegrationTest} covers the wire format for real).
 */
class NotificationStreamFanOutTest {

    private static final long USER = 710_001L;
    private static final long OTHER_USER = 710_002L;

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final NotificationEventCodec codec = new NotificationEventCodec(mapper);
    private final FakeChannel channel = new FakeChannel();

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ── cross-instance delivery ──────────────────────────────────────────────

    @Test
    void aBatchPublishedOnInstanceBReachesTheEmitterRegisteredOnInstanceA() {
        Instance a = instance("instance-a");
        Instance b = instance("instance-b");
        a.registry.subscribe(USER);
        a.registry.clearRecorded();

        b.registry.publishNotification(USER, notification(31L), 1L);

        // The frames arrive on A in the order B sent them, and the notification
        // payload survives the JSON hop intact.
        assertThat(a.registry.eventNames()).containsExactly("notification", "unread-count");
        JsonNode payload = json(a.registry.recorded().get(0).data());
        assertThat(payload.get("id").asLong()).isEqualTo(31L);
        assertThat(payload.get("type").asText()).isEqualTo("order.new");
        assertThat(payload.get("entityType").asText()).isEqualTo("order");
        assertThat(payload.get("entityId").asLong()).isEqualTo(9001L);
        assertThat(payload.get("params").get("orderNumber").asText()).isEqualTo("ORD-9001");
        assertThat(payload.get("params").get("total").asDouble()).isEqualTo(1240.0);
        assertThat(payload.get("createdAt").isTextual()).isTrue();
        assertThat(((Number) a.registry.recorded().get(1).data()).longValue()).isEqualTo(1L);

        // B owns no emitter for this user, so its own publish produced nothing locally.
        assertThat(b.registry.recorded()).isEmpty();
        // One message for the batch, not one per frame.
        assertThat(channel.payloads()).hasSize(1);
    }

    @Test
    void aBatchOnlyReachesTheInstanceThatOwnsTheEmitter() {
        Instance a = instance("instance-a");
        Instance b = instance("instance-b");
        a.registry.subscribe(USER);
        a.registry.subscribe(OTHER_USER);
        a.registry.clearRecorded();

        b.registry.publishUnreadCount(OTHER_USER, 7L);

        assertThat(a.registry.eventNames()).containsExactly("unread-count");
        assertThat(((Number) a.registry.recorded().get(0).data()).longValue()).isEqualTo(7L);
        assertThat(b.registry.recorded()).isEmpty();
    }

    // ── exactly once locally ─────────────────────────────────────────────────

    @Test
    void aLocalPublishReachesTheLocalEmitterExactlyOnce() {
        Instance a = instance("instance-a");
        a.registry.subscribe(USER);
        a.registry.clearRecorded();

        a.registry.publishNotification(USER, notification(32L), 4L);

        // A is subscribed to the channel and receives its own message back. The
        // origin check is the only thing between this assertion and four frames
        // (a local emit plus an echoed one, twice).
        assertThat(a.registry.eventNames()).containsExactly("notification", "unread-count");
        assertThat(channel.payloads()).hasSize(1);
    }

    // ── degrading honestly ───────────────────────────────────────────────────

    @Test
    void aRedisOutageKeepsLocalDeliveryTheTransactionAndTheLogsCalm() {
        Instance publisher = brokenInstance("instance-b");
        Instance owner = brokenInstance("instance-a");
        publisher.registry.subscribe(USER);
        owner.registry.subscribe(USER);
        publisher.registry.clearRecorded();
        owner.registry.clearRecorded();

        ListAppender<ILoggingEvent> logs = attachLogs();
        try {
            // This runs in an after-commit callback, where an exception is a
            // notification failing a transaction that already succeeded.
            assertThatCode(() -> publisher.registry.publishNotification(USER, notification(33L), 1L))
                    .doesNotThrowAnyException();

            // Degrade honestly: the instance that created the row still serves its
            // own streams. Only the cross-instance leg is lost; the poll covers it.
            assertThat(publisher.registry.eventNames()).containsExactly("notification", "unread-count");
            assertThat(owner.registry.recorded()).isEmpty();

            for (int i = 0; i < 19; i++) {
                publisher.registry.publishUnreadCount(USER, i);
            }
            assertThat(owner.registry.recorded()).isEmpty();

            // One warning for the whole outage, not one per notification, and no errors.
            assertThat(logs.list).filteredOn(event -> event.getLevel() == Level.WARN).hasSize(1);
            assertThat(logs.list).filteredOn(event -> event.getLevel() == Level.ERROR).isEmpty();
        } finally {
            detachLogs(logs);
        }
    }

    @Test
    void aRecoveredBrokerIsReportedOnceAndStopsTheWarning() {
        AtomicBoolean down = new AtomicBoolean(true);
        RedisNotificationBroadcaster broadcaster = new RedisNotificationBroadcaster(
                new FakeRedisTemplate(payload -> {
                    if (down.get()) throw new RedisConnectionFailureException("redis is down");
                    channel.publish(payload);
                }),
                codec, "instance-flaky", Runnable::run);
        List<NotificationFrame> frame = List.of(new NotificationFrame("unread-count", 1L));

        ListAppender<ILoggingEvent> logs = attachLogs();
        try {
            broadcaster.broadcast(USER, frame);
            broadcaster.broadcast(USER, frame);
            assertThat(logs.list).filteredOn(event -> event.getLevel() == Level.WARN).hasSize(1);

            down.set(false);
            broadcaster.broadcast(USER, frame);
            broadcaster.broadcast(USER, frame);

            assertThat(logs.list).filteredOn(event -> event.getLevel() == Level.WARN).hasSize(1);
            assertThat(logs.list)
                    .filteredOn(event -> event.getLevel() == Level.INFO)
                    .filteredOn(event -> event.getFormattedMessage().contains("recovered"))
                    .hasSize(1);
            assertThat(channel.payloads()).hasSize(2);
        } finally {
            detachLogs(logs);
        }
    }

    // ── after commit ─────────────────────────────────────────────────────────

    @Test
    void aRolledBackNotificationIsNeitherDeliveredNorPublished() {
        Instance a = instance("instance-a");
        a.registry.subscribe(USER);
        a.registry.clearRecorded();

        TransactionSynchronizationManager.initSynchronization();
        a.registry.publishNotification(USER, notification(34L), 1L);

        // Nothing may be announced before the commit: the row is written inside the
        // fan-out transaction, and a rollback removes it again.
        assertThat(a.registry.recorded()).isEmpty();
        assertThat(channel.payloads()).isEmpty();

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(a.registry.recorded()).isEmpty();
        assertThat(channel.payloads()).isEmpty();
    }

    @Test
    void aCommittedNotificationIsAnnouncedOnlyAfterTheCommit() {
        Instance a = instance("instance-a");
        Instance b = instance("instance-b");
        a.registry.subscribe(USER);
        a.registry.clearRecorded();

        TransactionSynchronizationManager.initSynchronization();
        try {
            b.registry.publishNotification(USER, notification(35L), 9L);

            assertThat(b.registry.recorded()).isEmpty();
            assertThat(a.registry.recorded()).isEmpty();
            assertThat(channel.payloads()).isEmpty();

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            assertThat(a.registry.eventNames()).containsExactly("notification", "unread-count");
            assertThat(channel.payloads()).hasSize(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Instance instance(String id) {
        return newInstance(id, channel::publish);
    }

    private Instance brokenInstance(String id) {
        return newInstance(id, payload -> {
            throw new RedisConnectionFailureException("redis is down");
        });
    }

    /**
     * One application instance: its own emitters, its own publisher and its own
     * subscription on the shared channel. The publish executor is the calling thread so
     * assertions do not have to wait for anything.
     */
    private Instance newInstance(String id, Consumer<String> sink) {
        RedisNotificationBroadcaster broadcaster = new RedisNotificationBroadcaster(
                new FakeRedisTemplate(sink), codec, id, Runnable::run);
        RecordingRegistry registry = new RecordingRegistry(broadcaster);
        RedisNotificationStreamSubscriber subscriber =
                new RedisNotificationStreamSubscriber(codec, registry, broadcaster.origin());
        channel.subscribe(subscriber);
        return new Instance(registry, broadcaster, subscriber);
    }

    private ListAppender<ILoggingEvent> attachLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(RedisNotificationBroadcaster.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detachLogs(ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(RedisNotificationBroadcaster.class)).detachAppender(appender);
    }

    private JsonNode json(Object data) {
        return mapper.readTree(mapper.writeValueAsString(data));
    }

    private static NotificationResponse notification(long id) {
        return NotificationResponse.builder()
                .id(id)
                .type("order.new")
                .entityType("order")
                .entityId(9001L)
                .params(Map.of("orderNumber", "ORD-9001", "total", 1240.0))
                .createdAt(Instant.parse("2026-10-29T08:15:30Z"))
                .build();
    }

    private record Instance(
            RecordingRegistry registry,
            RedisNotificationBroadcaster broadcaster,
            RedisNotificationStreamSubscriber subscriber) {
    }

    /** The pub/sub channel in one JVM: a payload goes to every subscriber, publisher included. */
    private static final class FakeChannel {

        private final List<RedisNotificationStreamSubscriber> subscribers = new CopyOnWriteArrayList<>();
        private final List<String> published = new CopyOnWriteArrayList<>();

        void subscribe(RedisNotificationStreamSubscriber subscriber) {
            subscribers.add(subscriber);
        }

        void publish(String payload) {
            published.add(payload);
            byte[] body = payload.getBytes(StandardCharsets.UTF_8);
            subscribers.forEach(subscriber -> subscriber.onMessage(new FakeMessage(body), null));
        }

        List<String> payloads() {
            return List.copyOf(published);
        }
    }

    private record FakeMessage(byte[] body) implements Message {

        @Override
        public byte[] getBody() {
            return body;
        }

        @Override
        public byte[] getChannel() {
            return RedisNotificationBroadcaster.CHANNEL.getBytes(StandardCharsets.UTF_8);
        }
    }

    /** Stands in for {@code RedisTemplate}: hands the payload to the channel, or throws. */
    private static final class FakeRedisTemplate extends RedisTemplate<String, String> {

        private final Consumer<String> sink;

        FakeRedisTemplate(Consumer<String> sink) {
            this.sink = sink;
        }

        @Override
        public Long convertAndSend(String channel, Object message) {
            sink.accept(String.valueOf(message));
            return 1L;
        }
    }

    /** The registry without a servlet container: records what would have been written. */
    private static final class RecordingRegistry extends NotificationStreamRegistry {

        private final List<NotificationFrame> written = Collections.synchronizedList(new ArrayList<>());

        RecordingRegistry(NotificationBroadcaster broadcaster) {
            super(60_000L, broadcaster);
        }

        @Override
        protected void send(SseEmitter emitter, Long userId, String event, Object data) {
            written.add(new NotificationFrame(event, data));
        }

        List<NotificationFrame> recorded() {
            return List.copyOf(written);
        }

        List<String> eventNames() {
            return recorded().stream().map(NotificationFrame::name).toList();
        }

        void clearRecorded() {
            written.clear();
        }
    }
}
