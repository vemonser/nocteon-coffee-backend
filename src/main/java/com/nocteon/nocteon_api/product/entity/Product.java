package com.nocteon.nocteon_api.product.entity;

import java.util.List;
import java.util.Set;

import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.HashSet;

import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.common.entity.SoftDeletableEntity;
import com.nocteon.nocteon_api.farm.entity.Farm;
import com.nocteon.nocteon_api.journal.entity.JournalPost;
import com.nocteon.nocteon_api.origin.entity.Origin;
import com.nocteon.nocteon_api.pairing.entity.Pairing;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.tastingNote.entity.TastingNote;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@Table(name = "products")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Product extends SoftDeletableEntity {

        @Column(nullable = false, unique = true)
        private String slug;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "category_id", nullable = false)
        private Category category;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "origin_id")
        private Origin origin;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "farm_id")
        private Farm farm;

        @Enumerated(EnumType.STRING)
        @Column(name = "product_type", nullable = false)
        private ProductType productType;

        @Column(nullable = false)
        @Builder.Default
        private boolean featured = false;

        /**
         * Social-preview image. {@code null} means "use the primary media", resolved when the
         * response is built — not copied here, so the card follows a cover change.
         */
        @Column(name = "og_image_url", columnDefinition = "TEXT")
        private String ogImageUrl;

        @Column(name = "is_active", nullable = false)
        @Builder.Default
        private boolean isActive = true;

        // ===== Relations =====

        @ManyToMany
        @JoinTable(name = "product_option_links", joinColumns = @JoinColumn(name = "product_id"), inverseJoinColumns = @JoinColumn(name = "option_id"))
        private Set<ProductOption> options = new HashSet<>();

        @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
        @Builder.Default
        @BatchSize(size = 30)
        private Set<ProductTranslation> translations = new HashSet<>();

        @OneToOne(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
        private CoffeeDetails coffeeDetails;

        @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
        @Builder.Default
        @BatchSize(size = 30)
        private Set<ProductVariant> variants = new HashSet<>();

        @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
        @Builder.Default
        @BatchSize(size = 30)
        private Set<ProductMedia> media = new HashSet<>();

        /**
         * Batched like the other collections on this entity, because the AI projection walks it
         * for every product it touches. Left unbatched it costs one {@code SELECT} per product,
         * which is an N+1 on any full-catalog read.
         */
        @ManyToMany(fetch = FetchType.LAZY)
        @JoinTable(name = "product_tasting_notes", joinColumns = @JoinColumn(name = "product_id"), inverseJoinColumns = @JoinColumn(name = "tasting_note_id"))
        @Builder.Default
        @BatchSize(size = 30)
        private List<TastingNote> tastingNotes = new ArrayList<>();

        @ManyToMany(fetch = FetchType.LAZY)
        @JoinTable(name = "product_pairings", joinColumns = @JoinColumn(name = "product_id"), inverseJoinColumns = @JoinColumn(name = "pairing_id"))
        @Builder.Default
        @BatchSize(size = 30)
        private List<Pairing> pairings = new ArrayList<>();

        /**
         * Merchandising labels (FE-10). Batched for the same reason as the other collections: the
         * detail response reads them, and anything walking many products must not pay one SELECT
         * per product. Never read by the list projection.
         */
        @ManyToMany(fetch = FetchType.LAZY)
        @JoinTable(name = "product_tag_links", joinColumns = @JoinColumn(name = "product_id"), inverseJoinColumns = @JoinColumn(name = "tag_id"))
        @Builder.Default
        @BatchSize(size = 30)
        private Set<ProductTag> tags = new HashSet<>();

        // BrewingMethod
        @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
        @Builder.Default
        @BatchSize(size = 30)
        private List<ProductBrewingMethod> brewingMethods = new ArrayList<>();

        @ManyToMany(fetch = FetchType.LAZY)
        @JoinTable(name = "product_journal_posts", joinColumns = @JoinColumn(name = "product_id"), inverseJoinColumns = @JoinColumn(name = "journal_post_id"))
        @Builder.Default
        @BatchSize(size = 30)
        private List<JournalPost> journalPosts = new ArrayList<>();
}