package com.nocteon.nocteon_api.journal.entity;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

import com.nocteon.nocteon_api.common.entity.SoftDeletableEntity;
import com.nocteon.nocteon_api.product.entity.Product;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@Table(name = "journal_posts")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class JournalPost extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_category_id", nullable = false)
    private JournalCategory journalCategory;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    @Column(nullable = false)
    @Builder.Default
    private boolean featured = false;

    @Column(name = "published_at")
    private Instant publishedAt;

    @OneToMany(mappedBy = "journalPost", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<JournalPostTranslation> translations = new ArrayList<>();

    @ManyToMany(mappedBy = "journalPosts", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Product> products = new ArrayList<>();
}
