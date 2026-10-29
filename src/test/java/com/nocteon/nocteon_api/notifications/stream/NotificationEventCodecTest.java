package com.nocteon.nocteon_api.notifications.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.nocteon.nocteon_api.notifications.dto.NotificationResponse;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Redis wire format: what another instance (or another version of it) is expected
 * to read back, and what it must do with a body it cannot understand.
 */
class NotificationEventCodecTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final NotificationEventCodec codec = new NotificationEventCodec(mapper);

    @Test
    void roundTripsBothFramesInOrder() {
        NotificationResponse notification = NotificationResponse.builder()
                .id(42L)
                .type("contact.message")
                .entityType("contact")
                .entityId(7L)
                .params(Map.of("subject", "Hello"))
                .createdAt(Instant.parse("2026-10-29T08:15:30Z"))
                .build();
        NotificationEvent event = new NotificationEvent("instance-a", 99L,
                List.of(new NotificationFrame("notification", notification),
                        new NotificationFrame("unread-count", 3L)));

        String payload = codec.encode(event);
        NotificationEvent decoded = codec.decode(payload.getBytes(StandardCharsets.UTF_8));

        assertThat(decoded.origin()).isEqualTo("instance-a");
        assertThat(decoded.userId()).isEqualTo(99L);
        assertThat(decoded.frames()).extracting(NotificationFrame::name)
                .containsExactly("notification", "unread-count");

        // The payload is a value to SET, never a delta: the count survives the hop
        // as the number it was published with.
        assertThat(((Number) decoded.frames().get(1).data()).longValue()).isEqualTo(3L);

        // Frame data is decoded generically (the receiving instance re-serializes it
        // through its own SSE converter), so compare the wire form, which is what the
        // browser ends up with.
        JsonNode data = mapper.readTree(mapper.writeValueAsString(decoded.frames().get(0).data()));
        assertThat(data.get("id").asLong()).isEqualTo(42L);
        assertThat(data.get("type").asText()).isEqualTo("contact.message");
        assertThat(data.get("params").get("subject").asText()).isEqualTo("Hello");
        assertThat(data.get("createdAt").asText()).isEqualTo("2026-10-29T08:15:30Z");
        assertThat(data.get("readAt").isNull()).isTrue();
    }

    @Test
    void ignoresABodyItCannotUnderstand() {
        assertThat(codec.decode(null)).isNull();
        assertThat(codec.decode(new byte[0])).isNull();
        assertThat(codec.decode("not json".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(codec.decode("{\"unexpected\":true}".getBytes(StandardCharsets.UTF_8))).isNull();
    }

    @Test
    void refusesToEncodeWhatItCannotSerialize() {
        // An unserializable frame is dropped instead of throwing on the after-commit
        // path, where the transaction has already committed.
        NotificationEvent event = new NotificationEvent("instance-a", 1L,
                List.of(new NotificationFrame("notification", new Exploding())));

        assertThat(codec.encode(event)).isNull();
    }

    /** Jackson reaches the getter and the getter fails; the batch must not escape. */
    static final class Exploding {
        public String getValue() {
            throw new IllegalStateException("cannot be serialized");
        }
    }
}
