package com.nocteon.nocteon_api.journal.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TranslationResponseDashboard {
    private String language;
    private String title;
    private String excerpt;
    private String content;
    private String metaTitle;
    private String metaDescription;
    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public void setExcerpt(String excerpt) {
        this.excerpt = excerpt;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getMetaTitle() {
        return metaTitle;
    }

    public void setMetaTitle(String metaTitle) {
        this.metaTitle = metaTitle;
    }

    public String getMetaDescription() {
        return metaDescription;
    }

    public void setMetaDescription(String metaDescription) {
        this.metaDescription = metaDescription;
    }


    // Manual builder for Lombok compatibility
    public static class TranslationResponseDashboardBuilder {
        private String language;
        private String title;
        private String excerpt;
        private String content;
        private String metaTitle;
        private String metaDescription;

        public TranslationResponseDashboardBuilder language(String language) {
            this.language = language;
            return this;
        }
        public TranslationResponseDashboardBuilder title(String title) {
            this.title = title;
            return this;
        }
        public TranslationResponseDashboardBuilder excerpt(String excerpt) {
            this.excerpt = excerpt;
            return this;
        }
        public TranslationResponseDashboardBuilder content(String content) {
            this.content = content;
            return this;
        }
        public TranslationResponseDashboardBuilder metaTitle(String metaTitle) {
            this.metaTitle = metaTitle;
            return this;
        }
        public TranslationResponseDashboardBuilder metaDescription(String metaDescription) {
            this.metaDescription = metaDescription;
            return this;
        }

        public TranslationResponseDashboard build() {
            TranslationResponseDashboard dto = new TranslationResponseDashboard();
            dto.setLanguage(language);
            dto.setTitle(title);
            dto.setExcerpt(excerpt);
            dto.setContent(content);
            dto.setMetaTitle(metaTitle);
            dto.setMetaDescription(metaDescription);
            return dto;
        }
    }
}