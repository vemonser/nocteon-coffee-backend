package com.nocteon.nocteon_api.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * One turn of the conversation.
 *
 * <p>A bean rather than a record so Jackson binds it the way every other request DTO in the
 * codebase binds, and so {@code @Valid} on the controller reports a field error against
 * {@code message} instead of a malformed-body 400.
 */
@Getter
@Setter
public class ChatRequest {

    /**
     * What the visitor typed.
     *
     * <p>There is no length cap here on purpose: a long message is a legitimate way to describe
     * a taste preference, and truncating it silently would answer a question nobody asked. The
     * cost is bounded downstream by the chat model's own context window.
     */
    @NotBlank(message = "{validation.chat.message.notBlank}")
    private String message;

    /**
     * Opaque handle for the conversation, returned by the first turn and echoed afterwards.
     *
     * Client-supplied and therefore not trusted for anything but keying a history entry — see
     * {@code AiChatSessionStore}, which treats it as a key and nothing more.
     */
    private String sessionId;

    /**
     * BCP-47 language tag from the client, e.g. {@code ar}.
     *
     * <p>Optional: {@code AiChatService} falls back to the request's {@code Accept-Language}
     * locale, so a client that sends nothing still gets the storefront's language.
     */
    private String locale;
}