package com.nocteon.nocteon_api.journal.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.nocteon.nocteon_api.journal.enums.JournalPostStatus;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dashboard projection of a journal post: identity, listing columns and every translation
 * (title/excerpt/content/meta) so the dashboard form can edit both languages at once.
 *
 * <p>Also used by the trash-bin listing, where only the subset of fields populated by the native
 * query (id, slug, coverImageUrl, featured, status, createdAt, translations with title) is filled
 * and the rest stay null.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
public class JournalPostResponseDashboard {
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
    private List<String> relatedProductSlugs;
    private Instant createdAt;
    private List<TranslationResponseDashboard> translations;
    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getCategorySlug() {
        return categorySlug;
    }

    public void setCategorySlug(String categorySlug) {
        this.categorySlug = categorySlug;
    }

    public String getAuthorUsername() {
        return authorUsername;
    }

    public void setAuthorUsername(String authorUsername) {
        this.authorUsername = authorUsername;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public boolean isFeatured() {
        return featured;
    }

    public void setFeatured(boolean featured) {
        this.featured = featured;
    }

    public JournalPostStatus getStatus() {
        return status;
    }

    public void setStatus(JournalPostStatus status) {
        this.status = status;
    }

    public int getReadingTimeMinutes() {
        return readingTimeMinutes;
    }

    public void setReadingTimeMinutes(int readingTimeMinutes) {
        this.readingTimeMinutes = readingTimeMinutes;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    public List<String> getRelatedProductSlugs() {
        return relatedProductSlugs;
    }

    public void setRelatedProductSlugs(List<String> relatedProductSlugs) {
        this.relatedProductSlugs = relatedProductSlugs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<TranslationResponseDashboard> getTranslations() {
        return translations;
    }

    public void setTranslations(List<TranslationResponseDashboard> translations) {
        this.translations = translations;
    }


    // Manual builder for Lombok compatibility
    public static class JournalPostResponseDashboardBuilder {
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
        private List<String> relatedProductSlugs;
        private Instant createdAt;
        private List<TranslationResponseDashboard> translations;

        public JournalPostResponseDashboardBuilder id(Long id) {
            this.id = id;
            return this;
        }
        public JournalPostResponseDashboardBuilder slug(String slug) {
            this.slug = slug;
            return this;
        }
        public JournalPostResponseDashboardBuilder categorySlug(String categorySlug) {
            this.categorySlug = categorySlug;
            return this;
        }
        public JournalPostResponseDashboardBuilder authorUsername(String authorUsername) {
            this.authorUsername = authorUsername;
            return this;
        }
        public JournalPostResponseDashboardBuilder coverImageUrl(String coverImageUrl) {
            this.coverImageUrl = coverImageUrl;
            return this;
        }
        public JournalPostResponseDashboardBuilder featured(boolean featured) {
            this.featured = featured;
            return this;
        }
        public JournalPostResponseDashboardBuilder status(JournalPostStatus status) {
            this.status = status;
            return this;
        }
        public JournalPostResponseDashboardBuilder readingTimeMinutes(int readingTimeMinutes) {
            this.readingTimeMinutes = readingTimeMinutes;
            return this;
        }
        public JournalPostResponseDashboardBuilder publishedAt(Instant publishedAt) {
            this.publishedAt = publishedAt;
            return this;
        }
        public JournalPostResponseDashboardBuilder tags(Set<String> tags) {
            this.tags = tags;
            return this;
        }
        public JournalPostResponseDashboardBuilder relatedProductSlugs(List<String> relatedProductSlugs) {
            this.relatedProductSlugs = relatedProductSlugs;
            return this;
        }
        public JournalPostResponseDashboardBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        public JournalPostResponseDashboardBuilder translations(List<TranslationResponseDashboard> translations) {
            this.translations = translations;
            return this;
        }

        public JournalPostResponseDashboard build() {
            JournalPostResponseDashboard dto = new JournalPostResponseDashboard();
            dto.setId(id);
            dto.setSlug(slug);
            dto.setCategorySlug(categorySlug);
            dto.setAuthorUsername(authorUsername);
            dto.setCoverImageUrl(coverImageUrl);
            dto.setFeatured(featured);
            dto.setStatus(status);
            dto.setReadingTimeMinutes(readingTimeMinutes);
            dto.setPublishedAt(publishedAt);
            dto.setTags(tags);
            dto.setRelatedProductSlugs(relatedProductSlugs);
            dto.setCreatedAt(createdAt);
            dto.setTranslations(translations);
            return dto;
        }
    }
}