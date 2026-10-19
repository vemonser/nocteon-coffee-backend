package com.nocteon.nocteon_api.journal.dto.response;

import java.time.Instant;
import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The trash-bin projection of a journal category: identity plus every translation name.
 *
 * <p>Categories carry no image, description or ordering column, so unlike the other lookup
 * entities this stays at the three fields the table actually has.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
public class JournalCategoryResponseDashboard {
    private Long id;
    private String slug;
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
    public static class JournalCategoryResponseDashboardBuilder {
        private Long id;
        private String slug;
        private Instant createdAt;
        private List<TranslationResponseDashboard> translations;

        public JournalCategoryResponseDashboardBuilder id(Long id) {
            this.id = id;
            return this;
        }
        public JournalCategoryResponseDashboardBuilder slug(String slug) {
            this.slug = slug;
            return this;
        }
        public JournalCategoryResponseDashboardBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        public JournalCategoryResponseDashboardBuilder translations(List<TranslationResponseDashboard> translations) {
            this.translations = translations;
            return this;
        }

        public JournalCategoryResponseDashboard build() {
            JournalCategoryResponseDashboard dto = new JournalCategoryResponseDashboard();
            dto.setId(id);
            dto.setSlug(slug);
            dto.setCreatedAt(createdAt);
            dto.setTranslations(translations);
            return dto;
        }
    }
}