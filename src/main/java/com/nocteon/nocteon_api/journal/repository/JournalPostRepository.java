package com.nocteon.nocteon_api.journal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.journal.entity.JournalPost;


public interface JournalPostRepository extends JpaRepository<JournalPost, Long> {
        @Query("""
                            SELECT DISTINCT jp FROM JournalPost jp
                            JOIN jp.translations t
                            WHERE LOWER(t.title) LIKE LOWER(CONCAT('%', :query, '%'))
                        """)
        List<JournalPost> searchJournalPosts(@Param("query") String query, Pageable pageable);

        /**
         * One post for staff, at any status.
         *
         * <p>{@code products} is deliberately not fetch-joined. {@code JournalPost.translations}
         * and {@code JournalPost.products} are both {@code List}, i.e. both bags, and Hibernate
         * refuses to fetch two of them in one query — it throws
         * {@code MultipleBagFetchException} at execution time, so this method failed for every
         * slug rather than only for some. {@code products} lazy-loads instead; this returns a
         * single post, so that is one extra query, not a per-row N+1.
         *
         * <p>Nothing detected this: it needs a database to execute, and the suite mocks
         * repositories. {@code JournalRepositoryFetchTest} pins the rule instead.
         */
        @Query("""
                        SELECT DISTINCT p FROM JournalPost p
                        LEFT JOIN FETCH p.translations
                        LEFT JOIN FETCH p.author
                        WHERE p.slug = :slug
                        """)
        Optional<JournalPost> findBySlugForDashboard(@Param("slug") String slug);

        @Query("""
                        SELECT DISTINCT p FROM JournalPost p
                        LEFT JOIN p.translations t
                        LEFT JOIN FETCH p.author
                        WHERE t.language = :language
                        AND p.status = com.nocteon.nocteon_api.journal.enums.JournalPostStatus.PUBLISHED
                        AND (COALESCE(:search, '') = ''
                             OR LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')))
                        AND (:categorySlug IS NULL OR p.journalCategory.slug = :categorySlug)
                        AND (:featured IS NULL OR p.featured = :featured)
                        ORDER BY p.publishedAt DESC
                        """)
        Page<JournalPost> findAllPublic(
                        @Param("language") String language,
                        @Param("search") String search,
                        @Param("categorySlug") String categorySlug,
                        @Param("featured") Boolean featured,
                        Pageable pageable);

        @Query("""
                        SELECT DISTINCT p FROM JournalPost p
                        LEFT JOIN p.translations t
                        LEFT JOIN FETCH p.author
                        WHERE (COALESCE(:search, '') = ''
                               OR  LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')))
                        AND (:categorySlug IS NULL OR p.journalCategory.slug = :categorySlug)
                        """)
        Page<JournalPost> findAllDashboard(
                        @Param("search") String search,
                        @Param("categorySlug") String categorySlug,
                        Pageable pageable);

        /**
         * A single post for the anonymous storefront, restricted to PUBLISHED.
         *
         * <p>The {@code status = PUBLISHED} predicate is load-bearing and mirrors
         * {@link #findAllPublic}, which filters the same way. Without it this query returned DRAFT
         * and ARCHIVED posts by slug to anyone who guessed the slug, because
         * {@code JournalPostStatus} is {@code DRAFT, PUBLISHED, ARCHIVED} and the post body is
         * returned in full. Staff use {@link #findBySlugForDashboard}, which deliberately sees
         * every status.
         *
         * <p>As with that method, {@code products} is not fetch-joined: it and {@code translations}
         * are both bags, and Hibernate cannot fetch two of them at once.
         */
        @Query("""
                        SELECT DISTINCT p FROM JournalPost p
                        LEFT JOIN FETCH p.translations t
                        LEFT JOIN FETCH p.author
                        WHERE p.slug = :slug
                        AND t.language = :language
                        AND p.status = com.nocteon.nocteon_api.journal.enums.JournalPostStatus.PUBLISHED
                        """)
        Optional<JournalPost> findPublishedBySlugAndLanguage(
                        @Param("slug") String slug,
                        @Param("language") String language);

        boolean existsBySlug(String slug);
}
