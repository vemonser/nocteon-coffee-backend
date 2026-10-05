package com.nocteon.nocteon_api.ai.service;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The store is a cache of conversation history, never a precondition for answering. The model
 * call is stateless, so a Redis outage costs the assistant its memory and nothing else; a
 * 500 on {@code POST /api/ai/chat} because history could not be read would be a strictly worse
 * outcome than forgetting what was said three turns ago.
 *
 * <p>Every test here therefore asserts that a Redis failure produces an <em>empty value</em>
 * rather than an exception, and {@link AiChatServiceTest} separately proves the service still
 * answers when the store misbehaves in a way the store does not catch.
 */
@ExtendWith(MockitoExtension.class)
class AiChatSessionStoreTest {

    private static final String SESSION_ID = "s1";
    private static final Duration TTL = Duration.ofMinutes(30);

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ListOperations<String, String> listOperations;

    private AiChatSessionStore store;

    @BeforeEach
    void setUp() {
        store = new AiChatSessionStore(redisTemplate, JsonMapper.builder().build());
    }

    private void givenListOps() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
    }

    // --- append ---------------------------------------------------------------------

    @Test
    void appendStoresBothHalvesOfTheTurn() {
        givenListOps();

        store.append(SESSION_ID, "recommend something", "Try Yirgacheffe");

        ArgumentCaptor<List<String>> pushed = ArgumentCaptor.forClass(List.class);
        verify(listOperations).rightPushAll(anyString(), pushed.capture());

        assertThat(pushed.getValue()).hasSize(2);
    }

    /**
     * The TTL is refreshed on every append, so an active conversation is never cut off mid-way
     * while an abandoned one still expires.
     */
    @Test
    void appendRefreshesTheThirtyMinuteExpiry() {
        givenListOps();

        store.append(SESSION_ID, "hi", "hello");

        verify(redisTemplate).expire(anyString(), eq(TTL));
    }

    @Test
    void appendTrimsTheListSoAConversationCannotGrowWithoutBound() {
        givenListOps();

        store.append(SESSION_ID, "hi", "hello");

        verify(listOperations).trim(anyString(), any(Long.class), any(Long.class));
    }

    @Test
    void appendDoesNotThrowWhenRedisIsDown() {
        when(redisTemplate.opsForList())
                .thenThrow(new RedisConnectionFailureException("connection refused"));

        assertThatCode(() -> store.append(SESSION_ID, "hi", "hello")).doesNotThrowAnyException();
    }

    @Test
    void appendDoesNotThrowWhenTheWriteFails() {
        givenListOps();
        doThrow(new RedisConnectionFailureException("connection refused"))
                .when(listOperations).rightPushAll(anyString(), anyList());

        assertThatCode(() -> store.append(SESSION_ID, "hi", "hello")).doesNotThrowAnyException();
    }

    @Test
    void appendIgnoresABlankSessionId() {
        store.append("  ", "hi", "hello");

        verify(redisTemplate, never()).opsForList();
    }

    // --- history ---------------------------------------------------------------------

    @Test
    void historyReturnsNothingWhenRedisIsDown() {
        when(redisTemplate.opsForList())
                .thenThrow(new RedisConnectionFailureException("connection refused"));

        assertThat(store.history(SESSION_ID)).isEmpty();
    }

    @Test
    void historyReturnsNothingForABlankSessionId() {
        assertThat(store.history("")).isEmpty();

        verify(redisTemplate, never()).opsForList();
    }

    @Test
    void historySurvivesAnEntryItCannotParse() {
        givenListOps();
        when(listOperations.range(anyString(), any(Long.class), any(Long.class)))
                .thenReturn(List.of("{\"role\":\"user\",\"content\":\"hi\"}", "{not json"));

        assertThat(store.history(SESSION_ID)).hasSize(1);
    }

    @Test
    void historyCapsHowManyTurnsAreReplayedIntoThePrompt() {
        givenListOps();
        String entry = "{\"role\":\"user\",\"content\":\"hi\"}";
        when(listOperations.range(anyString(), any(Long.class), any(Long.class)))
                .thenReturn(java.util.stream.IntStream.range(0, 500)
                        .mapToObj(i -> entry)
                        .toList());

        assertThat(store.history(SESSION_ID)).hasSizeLessThanOrEqualTo(AiChatSessionStore.MAX_REPLAYED_MESSAGES);
    }

    // --- clear -----------------------------------------------------------------------

    @Test
    void clearDeletesTheSessionKey() {
        store.clear(SESSION_ID);

        verify(redisTemplate).delete(anyString());
    }

    @Test
    void clearDoesNotThrowWhenRedisIsDown() {
        when(redisTemplate.delete(anyString()))
                .thenThrow(new RedisConnectionFailureException("connection refused"));

        assertThatCode(() -> store.clear(SESSION_ID)).doesNotThrowAnyException();
    }

    @Test
    void clearIgnoresABlankSessionId() {
        store.clear(null);

        verify(redisTemplate, never()).delete(anyString());
    }
}