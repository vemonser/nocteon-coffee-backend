package com.nocteon.nocteon_api.search.dto.response;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One dashboard-wide search.
 *
 * <p>{@code results} holds at most a handful of rows per type, so it is deliberately not the same
 * number as {@code totalCount}. {@code totalCount} is how many entities matched across every type,
 * and {@code countsByType} breaks that down per type, keyed by
 * {@link com.nocteon.nocteon_api.search.enums.SearchResultType#getRouteSegment()} so the client
 * receives a stable string rather than an enum name it would have to map itself.
 *
 * <p>The dashboard needs the split, not just the sum: "56 results" tells an admin nothing about
 * whether to look at products or users, whereas "12 products, 40 users" points straight at it.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalSearchResponse {

    /** The rows to display, capped per type. */
    private List<SearchResultDto> results;

    /** Total entities matched across all types, uncapped. */
    private int totalCount;

    /** Matched total per type, keyed by {@code SearchResultType.getRouteSegment()}. */
    @Builder.Default
    private Map<String, Integer> countsByType = Map.of();
}