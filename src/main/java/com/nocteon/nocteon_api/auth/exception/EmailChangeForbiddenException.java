package com.nocteon.nocteon_api.auth.exception;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

/**
 * Raised when a caller without {@code user:change_role} tries to change an
 * account's e-mail address.
 *
 * <p>An e-mail is the login identity: change it to an address you control, run
 * "forgot password", and you own the account — including an ADMIN account.
 * {@code PUT /api/dashboard/users/{id}} is gated on {@code user:update}, which
 * MODERATOR holds, so the address needs the same grant as the role.
 */
public class EmailChangeForbiddenException extends BaseApiException {

    public EmailChangeForbiddenException() {
        super("error.user.emailChangeForbidden", HttpStatus.FORBIDDEN);
    }
}
