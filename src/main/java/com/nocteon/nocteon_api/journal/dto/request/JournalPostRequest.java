package com.nocteon.nocteon_api.journal.dto.request;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.nocteon.nocteon_api.journal.enums.JournalPostStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JournalPostRequest {

    @NotBlank(message = "{validation.category.notBlank}")
    private String categorySlug;

    private boolean featured = false;

    @NotNull(message = "{validation.status.notNull}")
    private JournalPostStatus status = JournalPostStatus.DRAFT;

    private Instant publishedAt;
    private List<String> productSlugs;
    private Set<String> tags;

    @NotEmpty(message = "{validation.translations.notEmpty}")
    @Size(min = 2, message = "{validation.translations.size}")
    private List<@Valid JournalPostTranslationRequest> translations;
}
