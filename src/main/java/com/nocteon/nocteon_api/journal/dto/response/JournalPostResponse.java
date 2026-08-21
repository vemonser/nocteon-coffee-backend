package com.nocteon.nocteon_api.journal.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.nocteon.nocteon_api.journal.enums.JournalPostStatus;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class JournalPostResponse {
    private Long id;
    private String slug;
    private String categorySlug;
    private String authorUsername;
    private String coverImageUrl;
    private boolean featured;
    private JournalPostStatus status;
    private int readingTimeMinutes;
    private Instant publishedAt;
    private Set<String> tags;
    private String title;
    private String excerpt;
    private String content;
    private String metaTitle;
    private String metaDescription;
    private List<String> relatedProductSlugs;
}
