package com.nocteon.nocteon_api.auth.exception;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

/**
 * Raised when a caller without {@code user:change_role} tries to alter a user's role.
 *
 * <p>{@code PUT /api/dashboard/users/{id}} is gated on {@code user:update}, which MODERATOR holds,
 * and the body carries an optional {@code role}. Editing a profile and changing what an account is
 * authorised to do are different grants, so the role field is checked separately against
 * {@code user:change_role} rather than being covered by the route's own permission.
 */
public class RoleChangeForbiddenException extends BaseApiException {

    public RoleChangeForbiddenException() {
        super("error.user.roleChangeForbidden", HttpStatus.FORBIDDEN);
    }
}