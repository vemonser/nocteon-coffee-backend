package com.nocteon.nocteon_api.common.exception.invalid;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

/** A category is still referenced by posts, so it cannot be removed. */
public class JournalCategoryInUseException extends BaseApiException {
    private final long postCount;

    public JournalCategoryInUseException(long postCount) {
        super("error.journal.category.in.use", HttpStatus.CONFLICT, String.valueOf(postCount));
        this.postCount = postCount;
    }

    public long getPostCount() {
        return postCount;
    }
}
