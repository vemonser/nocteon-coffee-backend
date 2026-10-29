package com.nocteon.nocteon_api.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.security.JwtService;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.notifications.service.NotificationService;
import com.nocteon.nocteon_api.notifications.stream.NotificationStreamRegistry;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * End-to-end cover for {@code GET /api/dashboard/notifications/stream}: the SSE
 * handshake and headers, the heartbeat, both events on a real creation, the
 * registry releasing a client that goes away, and rejection of unauthenticated and
 * unauthorised callers.
 *
 * <p>Real HTTP rather than MockMvc on purpose: MockMvc buffers the emitter, so it can
 * neither observe the heartbeat timing nor a mid-stream write failure — the two
 * things that make this endpoint different from a normal JSON response.
 */
@SpringBootTest(classes = NocteonApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Timeout(60)
class NotificationStreamIntegrationTest {

    private static final String STREAM_PATH = "/api/dashboard/notifications/stream";
    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(15);

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationStreamRegistry streamRegistry;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<SseConnection> openConnections = new ArrayList<>();
    private final List<HttpClient> clients = new ArrayList<>();
    private Long adminId;
    private Long customerId;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
        // The heartbeat interval is not observable in a test at the production 25s;
        // the scheduler reads this property.
        registry.add("nocteon.notifications.stream.heartbeat-ms", () -> 150);
    }

    @AfterEach
    void cleanUp() {
        openConnections.forEach(SseConnection::close);
        openConnections.clear();
        clients.forEach(HttpClient::shutdownNow);
        clients.clear();
        if (adminId != null) {
            jdbcTemplate.update("delete from notifications where recipient_user_id = ?", adminId);
            userRepository.deleteById(adminId);
            adminId = null;
        }
        if (customerId != null) {
            userRepository.deleteById(customerId);
            customerId = null;
        }
    }

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    void rejectsAnUnauthenticatedRequest() throws Exception {
        HttpClient client = newClient();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(streamUri()).header("Accept", "text/event-stream").build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).doesNotContain("event:");
    }

    @Test
    void rejectsATokenWithoutTheNotificationAuthority() throws Exception {
        long id = createUser("CUSTOMER");
        HttpClient client = newClient();
        // No `Accept: text/event-stream` here on purpose. The app's global
        // AccessDenied handler cannot render its JSON body for a text/event-stream
        // client, so any endpoint answers 401 instead of 403 in that case (verified
        // against /api/dashboard/notifications too, so it is not specific to the
        // stream). With a normal Accept the answer is the honest 403, which is the
        // authorisation this endpoint shares with the REST endpoints.
        HttpResponse<String> response = client.send(
                request(id).build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(streamRegistry.subscriberCount(id)).isZero();
    }

    @Test
    void streamsHeadersAHeartbeatAndBothEventsWhenANotificationIsCreated() throws Exception {
        long userId = createUser("ADMIN");
        SseConnection sse = connect(userId);

        assertThat(sse.contentType()).startsWith("text/event-stream");
        assertThat(streamRegistry.subscriberCount(userId)).isEqualTo(1);

        // ready: proves the handshake completed, and is what lets the client reset
        // its reconnect backoff.
        SseEvent ready = sse.awaitEvent(NotificationStreamRegistry.EVENT_READY, EVENT_TIMEOUT);
        assertThat(objectMapper.readTree(ready.data()).get("userId").asLong()).isEqualTo(userId);

        // heartbeat: a proxy sees traffic well before its idle timeout.
        SseEvent heartbeat = sse.awaitEvent(NotificationStreamRegistry.EVENT_HEARTBEAT, EVENT_TIMEOUT);
        assertThat(objectMapper.readTree(heartbeat.data()).has("timestamp")).isTrue();

        long before = notificationService.getUnreadCount(userId);
        notificationService.createAndPublish(
                userId, "order.new",
                "{\"orderNumber\":\"ORD-9001\",\"customerName\":\"Ada\",\"total\":1240.0}",
                "order", 910_001L);

        SseEvent created = sse.awaitEvent(NotificationStreamRegistry.EVENT_NOTIFICATION, EVENT_TIMEOUT);
        JsonNode payload = objectMapper.readTree(created.data());
        assertThat(payload.get("id").asLong()).isPositive();
        assertThat(payload.get("type").asText()).isEqualTo("order.new");
        assertThat(payload.get("entityType").asText()).isEqualTo("order");
        assertThat(payload.get("entityId").asLong()).isEqualTo(910_001L);
        assertThat(payload.get("params").get("orderNumber").asText()).isEqualTo("ORD-9001");
        assertThat(payload.get("params").get("total").asDouble()).isEqualTo(1240.0);
        assertThat(payload.get("readAt").isNull()).isTrue();
        assertThat(payload.get("createdAt").asText()).isNotBlank();

        // unread-count carries the number itself, matching GET /unread-count.
        SseEvent count = sse.awaitEvent(NotificationStreamRegistry.EVENT_UNREAD_COUNT, EVENT_TIMEOUT);
        assertThat(Long.parseLong(count.data().trim())).isEqualTo(before + 1);

        // A duplicate creation is a no-op: the DB row is unique and the stream must
        // not announce it twice.
        notificationService.createAndPublish(
                userId, "order.new",
                "{\"orderNumber\":\"ORD-9001\",\"customerName\":\"Ada\",\"total\":1240.0}",
                "order", 910_001L);
        assertThat(sse.awaitEvent(NotificationStreamRegistry.EVENT_NOTIFICATION, Duration.ofSeconds(1))).isNull();
        assertThat(notificationService.getUnreadCount(userId)).isEqualTo(before + 1);
    }

    @Test
    void markingReadPushesTheNewCount() throws Exception {
        long userId = createUser("ADMIN");
        SseConnection sse = connect(userId);

        notificationService.createAndPublish(userId, "order.new", "{}", "order", 910_002L);
        SseEvent created = sse.awaitEvent(NotificationStreamRegistry.EVENT_NOTIFICATION, EVENT_TIMEOUT);
        long notificationId = objectMapper.readTree(created.data()).get("id").asLong();
        assertThat(sse.awaitEvent(NotificationStreamRegistry.EVENT_UNREAD_COUNT, EVENT_TIMEOUT).data().trim())
                .isEqualTo("1");

        notificationService.markRead(userId, notificationId);

        assertThat(sse.awaitEvent(NotificationStreamRegistry.EVENT_UNREAD_COUNT, EVENT_TIMEOUT).data().trim())
                .isEqualTo("0");
    }

    @Test
    void releasesTheSubscriberWhenTheClientDisconnects() throws Exception {
        long userId = createUser("ADMIN");
        HttpClient client = newClient();
        HttpResponse<Stream<String>> response = client.send(
                request(userId).header("Accept", "text/event-stream").build(),
                HttpResponse.BodyHandlers.ofLines());
        SseConnection sse = new SseConnection(response);
        sse.start();
        assertThat(streamRegistry.subscriberCount(userId)).isEqualTo(1);

        // A closed tab, then a hard close of the socket so the server's next write
        // fails. The registry must drop the emitter instead of keeping it forever.
        response.body().close();
        client.shutdownNow();

        Instant deadline = Instant.now().plusSeconds(15);
        while (Instant.now().isBefore(deadline) && streamRegistry.subscriberCount(userId) > 0) {
            // Heartbeats run on their own; one extra publish covers a container that
            // only notices the close on its next write.
            streamRegistry.publishUnreadCount(userId, 0L);
            Thread.sleep(100);
        }

        assertThat(streamRegistry.subscriberCount(userId)).isZero();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private long createUser(String role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .username("fe23-" + suffix)
                .email("fe23-" + suffix + "@example.test")
                .role(role)
                .provider(com.nocteon.nocteon_api.auth.enums.Provider.LOCAL)
                .password("not-used")
                .enabled(true)
                .isActive(true)
                .build());
        if ("ADMIN".equals(role)) {
            adminId = user.getId();
        } else {
            customerId = user.getId();
        }
        return user.getId();
    }

    private String tokenFor(long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        return jwtService.generateAccessToken(UserPrincipal.create(user));
    }

    private HttpRequest.Builder request(long userId) {
        return HttpRequest.newBuilder(streamUri())
                .header("Authorization", "Bearer " + tokenFor(userId));
    }

    private URI streamUri() {
        return URI.create("http://localhost:" + port + STREAM_PATH);
    }

    private HttpClient newClient() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        clients.add(client);
        return client;
    }

    private SseConnection connect(long userId) throws Exception {
        HttpClient client = newClient();
        HttpResponse<Stream<String>> response = client.send(
                request(userId).header("Accept", "text/event-stream").build(),
                HttpResponse.BodyHandlers.ofLines());
        SseConnection sse = new SseConnection(response);
        sse.start();
        openConnections.add(sse);
        return sse;
    }

    private record SseEvent(String name, String data) {
    }

    /**
     * Minimal SSE reader: a virtual thread pumps lines into a queue so an await can
     * time out instead of hanging the suite when an event never arrives.
     */
    private static final class SseConnection implements AutoCloseable {

        private static final String END_OF_STREAM = "\u0000eof";
        private final HttpResponse<Stream<String>> response;
        private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private volatile boolean closed;

        SseConnection(HttpResponse<Stream<String>> response) {
            this.response = response;
        }

        void start() {
            Thread.ofVirtual().name("sse-test-reader").start(() -> {
                try (Stream<String> body = response.body()) {
                    body.forEach(line -> lines.offer(line == null ? "" : line));
                } catch (Throwable t) {
                    failure.set(t);
                } finally {
                    lines.offer(END_OF_STREAM);
                }
            });
        }

        String contentType() {
            return response.headers().firstValue("content-type").orElse("");
        }

        /** Returns null when the event does not arrive before the timeout. */
        SseEvent awaitEvent(String name, Duration timeout) throws InterruptedException {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return null;
                String line = lines.poll(remaining, TimeUnit.NANOSECONDS);
                if (line == null) return null;
                if (END_OF_STREAM.equals(line)) {
                    Throwable error = failure.get();
                    if (error != null) {
                        fail("stream ended while waiting for '" + name + "': " + error);
                    }
                    return null;
                }
                if (line.isBlank() || line.startsWith(":")) continue;
                if (!line.startsWith("event:")) continue;
                String eventName = line.substring("event:".length()).trim();
                String data = readData();
                if (eventName.equals(name)) return new SseEvent(eventName, data);
            }
        }

        /** Collects the data lines of one event block; stops at the blank separator. */
        private String readData() throws InterruptedException {
            StringBuilder data = new StringBuilder();
            while (true) {
                String line = lines.poll(5, TimeUnit.SECONDS);
                if (line == null || END_OF_STREAM.equals(line) || line.isBlank()) break;
                if (line.startsWith("data:")) {
                    if (data.length() > 0) data.append('\n');
                    data.append(line.substring("data:".length()).stripLeading());
                }
            }
            return data.toString();
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            try {
                response.body().close();
            } catch (RuntimeException ignored) {
                // Already closed by the test itself.
            }
        }
    }
}
