package com.nocteon.nocteon_api.journal.dto.response;

import java.time.Instant;
import java.util.List;

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
    private String coverImageUrl;
    private boolean featured;
    private Instant publishedAt;
    private String title;
    private String excerpt;
    private String content;
    private String metaTitle;
    private String metaDescription;
    private List<String> relatedProductSlugs;
}
