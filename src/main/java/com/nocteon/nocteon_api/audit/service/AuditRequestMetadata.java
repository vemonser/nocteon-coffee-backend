package com.nocteon.nocteon_api.audit.service;

/**
 * The two fields an audit row records about where it came from: the client IP and the raw
 * {@code User-Agent}.
 *
 * <p>Both halves are read in one place — from the request the writing thread is serving, at the
 * moment the row is written — so no writing service has to thread {@code HttpServletRequest}
 * through its own API to record them (and none does).
 *
 * <p>{@link #NONE} is the honest answer for a write that has no request behind it: a scheduler, a
 * background worker, an internal service call, a test. The row then keeps a null IP and a null
 * user agent — the two columns are legitimately empty for a system action, and inventing
 * {@code 0.0.0.0} or {@code "unknown"} would make a system write look like a network write.
 */
public record AuditRequestMetadata(String ipAddress, String userAgent) {

    /** No request to read: the row records neither a source IP nor a user agent. */
    public static final AuditRequestMetadata NONE = new AuditRequestMetadata(null, null);
}
