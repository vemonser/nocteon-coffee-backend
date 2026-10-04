package com.nocteon.nocteon_api.ai.dto.response;

import com.nocteon.nocteon_api.product.dto.response.ProductCardResponse;

import lombok.Builder;
import lombok.Getter;

/**
 * One entry of a "you might also like" rail: the card to render, plus why it is on the rail.
 *
 * <p>{@code reason} is part of the contract rather than a convenience. Every item carries a
 * non-blank one, including on the attribute fallback path where nothing was actually shared —
 * a blank string renders as an empty chip that reads as a bug to the customer, so the fallback
 * substitutes a neutral phrase instead of returning nothing.
 *
 * <p>{@code score} is <em>not</em> comparable across requests, and not between the two ranking
 * paths either: the vector path produces a cosine similarity while the attribute path produces a
 * weighted attribute total. Within a single response it is monotone with the ordering, which is
 * the only thing the client uses it for.
 */
@Getter
@Builder
public class SimilarProductResponse {

    private ProductCardResponse product;

    private String reason;

    private double score;
}