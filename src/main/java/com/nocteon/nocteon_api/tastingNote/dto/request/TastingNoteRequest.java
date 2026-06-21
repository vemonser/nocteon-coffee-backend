package com.nocteon.nocteon_api.tastingNote.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TastingNoteRequest {
    @NotEmpty(message = "{validation.translations.notEmpty}")
    @Size(min = 2, message = "{validation.translations.size}")
    private List<@Valid TastingNoteTranslationRequest> translations;

}
