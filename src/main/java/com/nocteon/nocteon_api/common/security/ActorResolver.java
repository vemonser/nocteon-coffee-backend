package com.nocteon.nocteon_api.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nocteon.nocteon_api.auth.security.UserPrincipal;

/**
 * Who is acting, for the rows that have to say so.
 *
 * <p>The order endpoints take the principal as a parameter (they always have
 * one); the product form does not — it saves a whole aggregate and never sees the
 * request. Rather than thread a parameter through five layers, the rows that
 * need an actor read it here, and a call with no authentication (a scheduler, a
 * test, the payment webhook) is honestly labelled {@code system} instead of
 * guessing.
 *
 * <p>{@link #currentActor()} returns a display label for the row's own
 * {@code actor} column ({@code inventory_movements.actor}, built by hand).
 * {@link #currentAuditActor()} returns the two fields an audit row stores —
 * the user id AND the e-mail — and resolves to {@link AuditActor#NONE} rather
 * than the string {@code system}, because an audit row must keep both columns
 * null for a system action (see {@link AuditActor}).
 */
public final class ActorResolver {

    public static final String SYSTEM = "system";

    private ActorResolver() {
    }

    /** The signed-in admin's e-mail, or {@code system}. */
    public static String currentActor() {
        AuditActor actor = currentAuditActor();
        return actor.userEmail() == null ? SYSTEM : actor.userEmail();
    }

    /**
     * The signed-in admin, for an audit row.
     *
     * <p>Same source as {@link #currentActor()}, resolved through
     * {@link AuditActor#of(UserPrincipal)} so both halves (id and e-mail) come
     * from one place. A caller with no authenticated {@link UserPrincipal} — a
     * scheduler, an anonymous request, the payment webhook — records no actor
     * at all instead of borrowing one from a thread that no longer represents
     * the acting person.
     */
    public static AuditActor currentAuditActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return AuditActor.NONE;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            return AuditActor.of(userPrincipal);
        }
        return AuditActor.NONE;
    }
}
