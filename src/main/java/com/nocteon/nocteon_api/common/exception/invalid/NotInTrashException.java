package com.nocteon.nocteon_api.common.exception.invalid;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

/**
 * A permanent delete was asked for a row that is still live.
 *
 * <p>Permanent delete is the second half of a two-step gesture: move to trash, then remove
 * for good. A live row can still be recovered by the ordinary means, so purging it in one
 * request would skip the safety step the dashboard offers — it is a conflict with the row's
 * current state, not a malformed request, and the admin can fix it by trashing first.
 */
public class NotInTrashException extends BaseApiException {

    public NotInTrashException() {
        super("error.delete.notInTrash", HttpStatus.CONFLICT);
    }
}
