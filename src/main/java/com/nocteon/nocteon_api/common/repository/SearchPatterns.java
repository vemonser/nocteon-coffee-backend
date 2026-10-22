package com.nocteon.nocteon_api.common.repository;

import java.time.Instant;

/**
 * The shapes a list query uses for its free-text search and its date range.
 *
 * <p>Both exist for ONE reason: PostgreSQL cannot infer the type of a
 * parameter that only appears in an "is null" test or inside a
 * concatenation, and the query then fails with
 *
 * <pre>
 *   ERROR: function lower(bytea) does not exist
 *   ERROR: could not determine data type of parameter $3
 * </pre>
 *
 * <p>So the search reaches the query as a READY-MADE PATTERN — already
 * lower-cased, already wrapped in the wildcards — and the columns are
 * compared with LOWER() on the COLUMN side. The date bounds reach it as
 * INSTANTS that are never null: an absent bound is a wide-open one.
 *
 * <p>The alternative — normalising a null term to "" — does NOT work. The
 * type is inferred when the statement is PREPARED, so a null bind fails
 * whatever value it carries.
 */
public final class SearchPatterns {

    private SearchPatterns() {
    }

    /**
     * The search term as a LIKE pattern, lower-cased.
     *
     * <p>Null when there is no search: the query's "is null" guard then
     * selects everything. The pattern is NOT wrapped in LOWER() — the
     * columns are compared with LOWER() instead, because a LOWER() around
     * the pattern is what makes PostgreSQL infer bytea.
     *
     * @param term the term the admin typed, or null
     * @return the pattern, or null when there is no search
     */
    public static String like(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        return "%" + term.trim().toLowerCase() + "%";
    }

    /**
     * The widest range the queries accept, used when the filter has no
     * bound.
     *
     * <p>A parameter that appears in an "is null" test has no type there, so
     * a null bound fails with "could not determine data type of parameter".
     */
    public static final Instant ANY_TIME_FROM = Instant.parse("1970-01-01T00:00:00Z");
    public static final Instant ANY_TIME_TO = Instant.parse("2100-01-01T00:00:00Z");

    /** The lower bound of a date range: the wide-open one when there is none. */
    public static Instant from(Instant bound) {
        return bound == null ? ANY_TIME_FROM : bound;
    }

    /** The upper bound of a date range: the wide-open one when there is none. */
    public static Instant to(Instant bound) {
        return bound == null ? ANY_TIME_TO : bound;
    }
}
