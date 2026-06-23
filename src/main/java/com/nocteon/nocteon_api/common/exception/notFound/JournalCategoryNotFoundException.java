package com.nocteon.nocteon_api.common.exception.notFound;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class JournalCategoryNotFoundException extends BaseApiException {
    public JournalCategoryNotFoundException() {
        super("error.journalCategory.notFound", HttpStatus.NOT_FOUND);
    }
}