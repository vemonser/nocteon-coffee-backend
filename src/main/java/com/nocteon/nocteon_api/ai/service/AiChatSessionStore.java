package com.nocteon.nocteon_api.ai.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Conversation history, held in Redis with a short TTL.
 *
 * <h2>Why Redis being down must not break chat</h2>
 * The model call is stateless: history is a convenience that lets the assistant understand
 * "that one" three turns later, not a precondition for answering. Losing it costs the assistant
 * its memory of the conversation and nothing else. So every Redis call here catches everything,
 * logs at {@code warn} with the message only — a stack trace per request would flood the log
 * precisely when the outage is hardest to diagnose — and returns an empty value.
 *
 * <p>The alternative, propagating, would mean a 500 on {@code POST /api/ai/chat} because a cache
 * is unreachable. That is strictly worse in every scenario.
 *
 * <h2>Why a list and not one JSON blob</h2>
 * Appending is a single {@code RPUSH} instead of a read, a rewrite and a write. That matters
 * because two visitors sharing a session id — a duplicated tab, a retried request — would
 * otherwise lose each other's turn on every concurrent append.
 *
 * <p>The stored shape is {@code {"role": "...", "content": "..."}} rather than a serialized
 * {@link Message}, so the stored format does not move whenever the Spring AI message classes do.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiChatSessionStore {

    /**
     * Namespace for the conversation keys.
     *
     * <p>Prefixed so an operator flushing a single feature's state is a matter of pattern, and so
     * a visitor-chosen session id can never collide with another feature's key space.
     */
    private static final String KEY_PREFIX = "ai:chat:";

    /**
     * How long an untouched conversation survives.
     *
     * <p>Refreshed on every append, so an active visitor is never cut off mid-conversation while
     * an abandoned one still expires on its own. Thirty minutes is longer than any real coffee
     * conversation and short enough that history is not a record of what people asked.
     */
    private static final Duration TTL = Duration.ofMinutes(30);

    /**
     * Ceiling on the messages replayed into the prompt, and on the messages kept in Redis.
     *
     * <p>Twelve is roughly six turns, which is what fits comfortably alongside the system prompt
     * and the tool output in a 3B model's context. Without it, a long conversation would push the
     * real question out of the window and the assistant would start answering an older one.
     */
    public static final int MAX_REPLAYED_MESSAGES = 12;

    private static final int MAX_STORED_MESSAGES = 24;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Records one completed turn.
     *
     * <p>No-op when Redis is unreachable, and when the session id is blank — there is no
     * conversation to record without one, and inventing a key for it would leak.
     */
    public void append(String sessionId, String userMessage, String assistantReply) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }

        try {
            String key = key(sessionId);

            redisTemplate.opsForList().rightPushAll(key, List.of(
                    write(MessageType.USER.getValue(), userMessage),
                    write(MessageType.ASSISTANT.getValue(), assistantReply)));

            // -MAX_STORED_MESSAGES .. -1 keeps the newest window, so a long conversation cannot
            // grow the key without bound.
            redisTemplate.opsForList().trim(key, -MAX_STORED_MESSAGES, -1);
            redisTemplate.expire(key, TTL);
        } catch (Exception e) {
            log.warn("Chat history unavailable for session {}, continuing without it: {}",
                    redact(sessionId), e.getMessage());
        }
    }

    /**
     * The conversation so far, oldest first.
     *
     * @param sessionId the handle returned by the previous turn
     * @return up to {@link #MAX_REPLAYED_MESSAGES} messages; empty when there is no history, when
     *         Redis is unreachable, or when an entry cannot be parsed
     */
    public List<Message> history(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return List.of();
        }

        try {
            List<String> stored = redisTemplate.opsForList().range(key(sessionId), 0, -1);
            if (stored == null || stored.isEmpty()) {
                return List.of();
            }

            List<Message> messages = new ArrayList<>(Math.min(stored.size(), MAX_REPLAYED_MESSAGES));
            for (String entry : stored) {
                read(entry).ifPresent(messages::add);
            }

            if (messages.size() > MAX_REPLAYED_MESSAGES) {
                return List.copyOf(messages.subList(messages.size() - MAX_REPLAYED_MESSAGES, messages.size()));
            }
            return List.copyOf(messages);
        } catch (Exception e) {
            log.warn("Chat history unavailable for session {}, continuing without it: {}",
                    redact(sessionId), e.getMessage());
            return List.of();
        }
    }

    /**
     * Forgets a conversation.
     *
     * <p>No-op when Redis is unreachable — the entry expires on its own within {@link #TTL}, so a
     * failed delete is a delay rather than a leak.
     */
    public void clear(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }

        try {
            redisTemplate.delete(key(sessionId));
        } catch (Exception e) {
            log.warn("Could not clear chat session {}, it will expire on its own: {}",
                    redact(sessionId), e.getMessage());
        }
    }

    private String key(String sessionId) {
        return KEY_PREFIX + sessionId.trim();
    }

    /**
     * Hides all but the tail of a client-supplied session id from the log.
     *
     * <p>The id is chosen by whoever calls the endpoint, so it is untrusted input; logging it whole
     * would let a caller write arbitrary content into the application log.
     */
    private static String redact(String sessionId) {
        String trimmed = sessionId.trim();
        return trimmed.length() <= 4 ? "****" : "****" + trimmed.substring(trimmed.length() - 4);
    }

    private String write(String role, String content) {
        try {
            return objectMapper.writeValueAsString(new StoredMessage(role, content));
        } catch (Exception e) {
            // Unreachable with the default String-based fields, but a record is not a guarantee
            // and a serialisation failure must not be allowed to break the turn.
            log.warn("Could not serialize chat message: {}", e.getMessage());
            return null;
        }
    }

    private Optional<Message> read(String entry) {
        if (!StringUtils.hasText(entry)) {
            return Optional.empty();
        }

        try {
            StoredMessage message = objectMapper.readValue(entry, StoredMessage.class);
            String content = message == null ? null : message.content();
            if (!StringUtils.hasText(content)) {
                return Optional.empty();
            }

            return Optional.of(MessageType.ASSISTANT.getValue().equals(message.role())
                    ? new AssistantMessage(content)
                    : new UserMessage(content));
        } catch (Exception e) {
            // One unparseable entry — a schema change, a partial write — drops that entry, not the
            // conversation around it.
            log.warn("Skipping unreadable chat history entry: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** The stored wire shape. Unknown fields are ignored so an older entry stays readable. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StoredMessage(String role, String content) {
    }
}