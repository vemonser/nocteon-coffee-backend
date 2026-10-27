package com.nocteon.nocteon_api.auth.exception;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

/**
 * Raised when a caller tries to block an administrator without being one.
 *
 * <p>{@code PATCH /api/dashboard/users/{id}/toggle-active} is gated on
 * {@code user:update}, which MODERATOR holds, so a moderator could otherwise
 * disable the account that owns the store.
 */
public class AdminAccountProtectedException extends BaseApiException {

    public AdminAccountProtectedException() {
        super("error.user.adminProtected", HttpStatus.FORBIDDEN);
    }
}
