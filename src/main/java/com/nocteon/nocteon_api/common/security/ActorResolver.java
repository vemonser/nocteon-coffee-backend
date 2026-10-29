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
 */
public final class ActorResolver {

    public static final String SYSTEM = "system";

    private ActorResolver() {
    }

    /** The signed-in admin's e-mail, or {@code system}. */
    public static String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return SYSTEM;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal
                && userPrincipal.getUser() != null
                && userPrincipal.getUser().getEmail() != null) {
            return userPrincipal.getUser().getEmail();
        }
        return SYSTEM;
    }
}
