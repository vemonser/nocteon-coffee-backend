package com.nocteon.nocteon_api.common.exception.invalid;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

/**
 * A row cannot be removed because something still points at it.
 *
 * <p>Carries the count and a message key that names the referencing thing ("still linked to 3
 * products"), so the refusal is actionable: the admin knows which screen to open. Both parts
 * come from {@link com.nocteon.nocteon_api.common.service.TrashRules}, which owns the decision;
 * this class only turns it into an HTTP answer.
 *
 * <p>The keyless form stays for the generic case — "still used elsewhere" — where nothing more
 * specific can be said (a database refusal on a reference the rules do not enumerate).
 */
public class EntityInUseException extends BaseApiException {

    private final long count;

    public EntityInUseException() {
        super("error.delete.referenced", HttpStatus.CONFLICT);
        this.count = 0;
    }

    public EntityInUseException(String messageKey, long count) {
        super(messageKey, HttpStatus.CONFLICT, String.valueOf(count));
        this.count = count;
    }

    /** How many rows still reference the entity; 0 for the generic form. */
    public long getCount() {
        return count;
    }
}
