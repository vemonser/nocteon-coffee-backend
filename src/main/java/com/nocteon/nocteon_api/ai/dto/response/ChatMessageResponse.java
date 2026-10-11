package com.nocteon.nocteon_api.ai.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * One entry of a stored conversation, as the client sees it.
 *
 * <p>A purpose-built type rather than Spring AI's own {@code Message}, which is an interface over
 * several concrete message classes and would leak the model's internal message shape — and its
 * constructor-only fields — straight into a public JSON contract.
 */
@Getter
@Builder
public class ChatMessageResponse {

    /** {@code user}, {@code assistant} or {@code system}. */
    private String role;

    private String content;
}