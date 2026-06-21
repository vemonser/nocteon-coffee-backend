package com.nocteon.nocteon_api.common.exception.notFound;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class TastingNoteNotFoundException extends BaseApiException {
    public TastingNoteNotFoundException() {
        super("error.tastingNote.notFound", HttpStatus.NOT_FOUND);
    }
}