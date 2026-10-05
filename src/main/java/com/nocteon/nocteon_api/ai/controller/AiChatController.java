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

import com.nocteon.nocteon_api.ai.dto.request.ChatRequest;
import com.nocteon.nocteon_api.ai.dto.response.ChatMessageResponse;
import com.nocteon.nocteon_api.ai.dto.response.ChatResponse;
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
 */
@RestController
@RequestMapping("/api/ai/chat")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;
    private final AiChatSessionStore sessionStore;

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
     * @param sessionId the handle a previous turn returned
     * @return the messages, in order; empty when there is no history
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> history(
            @RequestParam String sessionId) {

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
     * @param sessionId the handle to forget
     */
    @DeleteMapping("/session")
    public ResponseEntity<ApiResponse<Void>> clear(@RequestParam String sessionId) {

        sessionStore.clear(sessionId);

        return ResponseEntity.ok(ApiResponse.success(null, "Session cleared"));
    }

    /** Keeps Spring AI's message types out of the JSON contract. */
    private static ChatMessageResponse toResponse(Message message) {
        return ChatMessageResponse.builder()
                .role(message.getMessageType().getValue())
                .content(message.getText())
                .build();
    }
}