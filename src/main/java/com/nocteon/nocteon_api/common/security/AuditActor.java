package com.nocteon.nocteon_api.common.security;

import com.nocteon.nocteon_api.auth.security.UserPrincipal;

/**
 * The two fields an audit row records about who acted: the user id AND the e-mail.
 *
 * <p>Both halves are derived in one place so no call site can record an id without
 * an e-mail (or the reverse), which is the defect this type exists to prevent. A
 * call with no principal — a scheduler, a test, an internal job — resolves to
 * {@link #NONE}: the row honestly has no actor rather than the actor of whoever
 * happens to sit in the {@code SecurityContext} of the current thread.
 *
 * <p>The e-mail is recorded exactly as the principal stores it (lower-cased at
 * registration), never re-formatted: an audit row is evidence, not a display name.
 * A blank e-mail becomes null — an empty string would render as an empty Actor cell
 * instead of the page's "System" fallback.
 */
public record AuditActor(Long userId, String userEmail) {

    /** No authenticated actor: the row keeps a null id and a null e-mail. */
    public static final AuditActor NONE = new AuditActor(null, null);

    public static AuditActor of(UserPrincipal principal) {
        // UserPrincipal#getUserId reads through to the user entity, so a principal
        // without one has no id to record either.
        if (principal == null || principal.getUser() == null) {
            return NONE;
        }
        String email = principal.getUser().getEmail();
        if (email != null) {
            email = email.trim();
            if (email.isEmpty()) {
                email = null;
            }
        }
        return new AuditActor(principal.getUserId(), email);
    }
}
