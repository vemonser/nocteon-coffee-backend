package com.nocteon.nocteon_api.audit.service;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.nocteon.nocteon_api.common.util.ClientIpResolver;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads the client IP and {@code User-Agent} of the request the current thread is serving, for
 * the audit row being written.
 *
 * <p>The request is taken from {@link RequestContextHolder}, which Spring MVC populates for the
 * duration of a request on the thread that handles it. That is what keeps the audit service's
 * signature free of {@code HttpServletRequest}: a writer calls
 * {@code auditLogService.logSuccess(...)} exactly as before, and this resolver fills the two
 * source columns at write time. No bean is request-scoped, so no caller pays for a proxy.
 *
 * <p>Off a request thread — a scheduler, a worker pool, a test — the holder is empty and this
 * returns {@link AuditRequestMetadata#NONE}. That path is exercised by
 * {@code AuditRequestMetadataIntegrationTest#aWorkerThreadWithNoRequestStoresNullsAndDoesNotThrow}.
 *
 * <p>The IP is not resolved here twice: it is {@link ClientIpResolver}, the same resolver the rate
 * limiter uses, so the audit row honours exactly the same proxy rule — {@code X-Real-IP} (and then
 * {@code X-Forwarded-For}) only when the immediate peer is a configured
 * {@code rate-limit.trusted-proxies} entry, and the socket address otherwise. A header from an
 * untrusted peer therefore cannot choose the IP an audit row records.
 *
 * <p>Both values are truncated to what {@code public.audit_logs} holds, because a header is
 * attacker-controlled input and an oversized one must not fail the action it is only supposed to
 * describe.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditRequestMetadataResolver {

    static final String USER_AGENT_HEADER = "User-Agent";

    /** {@code audit_logs.ip_address} is {@code varchar(45)}: the longest textual IPv6 address. */
    static final int MAX_IP_LENGTH = 45;

    /**
     * {@code audit_logs.user_agent} is {@code text}, so this is not a schema limit but a cap on
     * hostile input: a real user agent is a few hundred characters at most, while a client may
     * send a megabyte. Keeping the prefix preserves what a human reads in the audit drawer
     * (browser, OS) and drops the padding.
     */
    static final int MAX_USER_AGENT_LENGTH = 512;

    private final ClientIpResolver clientIpResolver;

    /** The current request's IP and user agent, or {@link AuditRequestMetadata#NONE}. */
    public AuditRequestMetadata resolve() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return AuditRequestMetadata.NONE;
        }
        try {
            return new AuditRequestMetadata(
                    truncate(clientIpResolver.resolve(request), MAX_IP_LENGTH),
                    truncate(userAgent(request), MAX_USER_AGENT_LENGTH));
        } catch (RuntimeException e) {
            // An audit write is a side effect of an action that has already happened: it must
            // never turn a successful block/role change into a 500. Losing the source columns for
            // one row is the cheaper failure, and it is visible in the row itself (both null).
            log.debug("Could not read the request metadata for an audit row: {}", e.getMessage());
            return AuditRequestMetadata.NONE;
        }
    }

    /**
     * The request bound to this thread, or null when the thread is not serving one.
     *
     * <p>The holder is thread-bound and cleared by the dispatcher after the response, so a
     * worker thread never sees another request's attributes; when the binding is not a servlet
     * one (a non-web context in a test) there is no request to read either.
     */
    static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }

    /** The raw header, or null — never a placeholder for a client that sent none. */
    private static String userAgent(HttpServletRequest request) {
        String value = request.getHeader(USER_AGENT_HEADER);
        return value == null || value.isBlank() ? null : value;
    }

    static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
