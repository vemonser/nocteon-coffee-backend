package com.nocteon.nocteon_api.journal.dto.request;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JournalPostRequest {

    @NotBlank(message = "{validation.category.notBlank}")
    private String categorySlug;

    private boolean featured = false;
    private Instant publishedAt;
    private List<String> productSlugs;

    @NotEmpty(message = "{validation.translations.notEmpty}")
    @Size(min = 2, message = "{validation.translations.size}")
    private List<@Valid JournalPostTranslationRequest> translations;
}
