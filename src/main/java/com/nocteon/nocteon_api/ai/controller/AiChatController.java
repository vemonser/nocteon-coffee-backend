package com.nocteon.nocteon_api.ai.controller;

import java.util.List;

import org.springframework.ai.chat.messages.Message;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nocteon.nocteon_api.ai.config.AiProperties;
import com.nocteon.nocteon_api.ai.dto.request.ChatRequest;
import com.nocteon.nocteon_api.ai.dto.response.ChatMessageResponse;
import com.nocteon.nocteon_api.ai.dto.response.ChatResponse;
import com.nocteon.nocteon_api.ai.exception.AiUnavailableException;
import com.nocteon.nocteon_api.ai.service.AiChatService;
import com.nocteon.nocteon_api.ai.service.AiChatSessionStore;
import com.nocteon.nocteon_api.common.dto.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The coffee assistant, over HTTP.
 *
 * <p>Public on purpose, and it is worth being explicit about why that is safe here: there is no
 * authenticated principal in this flow at all. No cart, order or wishlist tool exists for the
 * assistant to reach, and the session id is a caller-chosen key over a thirty-minute cache of
 * that caller's own words — never an identity. {@code /api/dashboard/**} is untouched and stays
 * behind {@code ai:manage}.
 *
 * <p>No {@code @PreAuthorize} for the same reason as {@code AiSearchController}: this sits next
 * to the public catalog and is read by the same unauthenticated visitors.
 *
 * <h2>The flag applies to all three endpoints</h2>
 * {@code POST /api/ai/chat}, {@code GET /history} and {@code DELETE /session} are one feature, so
 * the {@code AI_CHATBOT_ENABLED} gate covers all three: {@link #requireChatbotEnabled()} is the
 * single check, and it raises the same 503 the turn itself raises. Gating only the turn would leave
 * a widget reading and clearing conversation state for an assistant that is switched off — and
 * would answer "here is your history" to a client that needed to hear "there is no assistant".
 */
@RestController
@RequestMapping("/api/ai/chat")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;
    private final AiChatSessionStore sessionStore;
    private final AiProperties properties;

    /**
     * One turn.
     *
     * <p>Returns 200 with a reply for every question the assistant can answer and every
     * infrastructure failure behind it — the degraded replies are still answers a shopper can act
     * on. A 503 comes only from {@code AiUnavailableException}, i.e. the feature being switched
     * off, which is a real answer to "is the assistant available" rather than a degradation.
     *
     * @param request the visitor's message, plus the optional session and locale
     * @return the reply, the product rail it cites, and the session handle to send next time
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@Valid @RequestBody ChatRequest request) {
        requireChatbotEnabled();

        return ResponseEntity.ok(ApiResponse.success(
                aiChatService.chat(request.getMessage(), request.getSessionId(), request.getLocale()),
                "Reply generated"));
    }

    /**
     * The stored conversation, oldest first.
     *
     * <p>Exposed so a client can restore a widget across a reload; empty when the session has
     * expired, which is the normal case after thirty minutes and is not an error.
     *
     * <p>503 while the chatbot is off, exactly like {@link #chat}: a client asking for history has
     * the same question the flag answers — "is the assistant available?" — and a 200 with an empty
     * list would tell it the assistant is available and has nothing to say.
     *
     * @param sessionId the handle a previous turn returned
     * @return the messages, in order; empty when there is no history
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> history(
            @RequestParam String sessionId) {
        requireChatbotEnabled();

        List<ChatMessageResponse> messages = sessionStore.history(sessionId).stream()
                .map(AiChatController::toResponse)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(messages, "History retrieved"));
    }

    /**
     * Forgets a conversation.
     *
     * <p>Returns 200 even when Redis is unreachable: the entry expires on its own within its TTL,
     * so a failed delete is a delay rather than a failure the client can act on.
     *
     * <p>503 while the chatbot is off, for the same reason as {@link #history} — and with the same
     * consequence for the caller: a 503 here means "do not bother", which is the answer a widget
     * behind a disabled assistant needs in order to hide itself.
     *
     * @param sessionId the handle to forget
     */
    @DeleteMapping("/session")
    public ResponseEntity<ApiResponse<Void>> clear(@RequestParam String sessionId) {
        requireChatbotEnabled();

        sessionStore.clear(sessionId);

        return ResponseEntity.ok(ApiResponse.success(null, "Session cleared"));
    }

    /**
     * The one flag check for all three endpoints, so the controller cannot end up with the same
     * gate applied to two of them.
     *
     * <p>{@link AiChatService} throws {@link AiUnavailableException} itself, which is the right
     * place for the rule on the turn that costs money; repeating it here keeps {@code /history} and
     * {@code DELETE /session} — which talk only to Redis and would otherwise answer with a stale
     * transcript of a conversation the flag says should not exist. The check is deliberately the
     * same conjunction the service uses: both flags, not just the chatbot one.
     *
     * @throws com.nocteon.nocteon_api.ai.exception.AiUnavailableException when AI or the chatbot is off
     */
    private void requireChatbotEnabled() {
        if (!properties.isEnabled() || !properties.isChatbotEnabled()) {
            throw new AiUnavailableException();
        }
    }

    /** Keeps Spring AI's message types out of the JSON contract. */
    private static ChatMessageResponse toResponse(Message message) {
        return ChatMessageResponse.builder()
                .role(message.getMessageType().getValue())
                .content(message.getText())
                .build();
    }
}