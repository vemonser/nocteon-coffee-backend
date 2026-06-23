package com.nocteon.nocteon_api.common.exception.notFound;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class JournalPostNotFoundException extends BaseApiException {
    public JournalPostNotFoundException() {
        super("error.journal.notFound", HttpStatus.NOT_FOUND);
    }
}