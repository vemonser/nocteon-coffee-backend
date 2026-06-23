package com.nocteon.nocteon_api.journal.dto.request;

import com.nocteon.nocteon_api.common.dto.TranslationRequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JournalPostTranslationRequest implements TranslationRequest {

    @NotBlank(message = "{validation.name.notBlank}")
    private String title;

    @Size(max = 500, message = "{validation.description.size}")
    private String excerpt;

    @NotBlank(message = "{validation.content.notBlank}")
    private String content;

    private String metaTitle;
    private String metaDescription;

    @NotBlank(message = "{validation.language.notBlank}")
    private String language;

    @Override
    public String getName() { return title; }
}