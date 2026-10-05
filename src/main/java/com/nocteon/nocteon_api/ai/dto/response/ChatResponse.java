package com.nocteon.nocteon_api.ai.dto.response;

import java.util.List;

import com.nocteon.nocteon_api.product.dto.response.ProductCardResponse;

import lombok.Builder;
import lombok.Getter;

/**
 * What the assistant said, and what the storefront should show next to it.
 *
 * <p>{@code products} is the rail, and it is empty far more often than not: a follow-up question
 * ("how do I brew it?") needs no cards, and neither does a turn the assistant had to decline.
 * That is why it is a list rather than a nullable field.
 */
@Getter
@Builder
public class ChatResponse {

    /** The assistant's message, already in the visitor's language. */
    private String reply;

    /**
     * Cards for the coffees the assistant cited, in the order it ranked them.
     *
     * <p>Resolved from slugs the {@code search_products} tool actually returned, so a card can
     * only appear here for a product that exists and is on sale.
     */
    private List<ProductCardResponse> products;

    /**
     * The conversation handle to send with the next turn.
     *
     * <p>Generated when the client supplied none, so a first-time visitor can keep talking
     * without having to create anything.
     */
    private String sessionId;
}