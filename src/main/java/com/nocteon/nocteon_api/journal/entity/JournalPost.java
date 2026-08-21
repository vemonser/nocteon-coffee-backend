package com.nocteon.nocteon_api.journal.entity;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;

import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.common.entity.SoftDeletableEntity;
import com.nocteon.nocteon_api.journal.enums.JournalPostStatus;
import com.nocteon.nocteon_api.product.entity.Product;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    @Column(nullable = false)
    @Builder.Default
    private boolean featured = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private JournalPostStatus status = JournalPostStatus.DRAFT;

    @Column(name = "reading_time_minutes", nullable = false)
    @Builder.Default
    private int readingTimeMinutes = 0;

    @Column(name = "published_at")
    private Instant publishedAt;

    @ElementCollection
    @CollectionTable(name = "journal_post_tags", joinColumns = @JoinColumn(name = "journal_post_id"))
    @Column(name = "tag")
    @Builder.Default
    private Set<String> tags = new HashSet<>();

    @OneToMany(mappedBy = "journalPost", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @BatchSize(size = 30)
    private List<JournalPostTranslation> translations = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "product_journal_posts", joinColumns = @JoinColumn(name = "journal_post_id"), inverseJoinColumns = @JoinColumn(name = "product_id"))
    @Builder.Default
    private List<Product> products = new ArrayList<>();
}
