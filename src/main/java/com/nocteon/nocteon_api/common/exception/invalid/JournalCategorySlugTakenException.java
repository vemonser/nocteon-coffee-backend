package com.nocteon.nocteon_api.common.exception.invalid;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

/** Restoring a trashed category would collide with a live category's slug. */
public class JournalCategorySlugTakenException extends BaseApiException {
    public JournalCategorySlugTakenException() {
        super("error.journal.category.slugTaken", HttpStatus.CONFLICT);
    }
}
