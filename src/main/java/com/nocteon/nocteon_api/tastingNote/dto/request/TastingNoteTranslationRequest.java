package com.nocteon.nocteon_api.tastingNote.dto.request;

import com.nocteon.nocteon_api.common.dto.TranslationRequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TastingNoteTranslationRequest implements TranslationRequest {

    @NotBlank(message = "{validation.name.notBlank}")
    @Size(min = 2, max = 100, message = "{validation.name.size}")
    private String name;

    @NotBlank(message = "{validation.language.notBlank}")
    private String language;
}