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
                /**
         * Rank tiers, weakest to strongest: 3 anywhere, 2 whole word, 1 prefix, 0 exact.
         *
         * <p>Matches the slug as well as the title. Products and categories were searchable by
         * slug long before this was, so an admin who typed a post's slug found products and
         * categories but nothing in the journal — the omission was an oversight, not a policy.
         *
         * <p>Ties break on the slug rather than the title, because titles are per-locale and an
         * Arabic and an English title would otherwise interleave in one alphabetical sequence.
         */
        @Query("""
                        SELECT jp FROM JournalPost jp
                        JOIN jp.translations t
                        WHERE LOWER(t.title) LIKE :query
                           OR LOWER(jp.slug) LIKE :query
                        GROUP BY jp.id, jp.slug
                        ORDER BY MIN(CASE
                                WHEN LOWER(jp.slug) = LOWER(:query) THEN 0
                                WHEN LOWER(t.title) = LOWER(:query) THEN 0
                                WHEN LOWER(jp.slug) LIKE :query THEN 1
                                WHEN LOWER(t.title) LIKE :query THEN 1
                                WHEN LOWER(t.title) LIKE :query THEN 2
                                ELSE 3
                            END),
                            MIN(LOWER(jp.slug))
                        """)
        List<JournalPost> searchJournalPosts(@Param("query") String query, Pageable pageable);

        /**
         * How many rows the search query matches, uncapped.
         *
         * <p>The search takes a fixed-size page of rows, so the returned list cannot be used
         * to report a total: it is the page size, not the match count. This mirrors the row
         * query's predicates exactly so the two cannot drift apart.
         */
        @Query("""
                        SELECT COUNT(DISTINCT jp) FROM JournalPost jp JOIN jp.translations t WHERE LOWER(t.title) LIKE :query OR LOWER(jp.slug) LIKE :query
                        """)
        long countSearchJournalPosts(@Param("query") String query);

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
                             OR LOWER(t.title) LIKE :search)
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
                               OR  LOWER(t.title) LIKE :search)
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

        /**
         * Trashed posts for the dashboard's deleted-items table.
         *
         * <p>Native because {@code @SQLRestriction} on {@code SoftDeletableEntity} filters
         * {@code deleted_at IS NOT NULL} out of every JPQL and derived query, so there is no way
         * to ask for these rows through the entity manager.
         *
         * <p>The post body, excerpt and SEO pair are not projected. A deleted-posts list renders
         * titles, and pulling every post body to fill a table of names is the same cost as loading
         * the storefront. {@code JournalPostResponseDashboard} is shaped to match.
         *
         * <p>{@code GROUP BY} collapses the one-row-per-language join back to one row per
         * (post, language) pair, which is the granularity the DTO's translation list wants.
         */
        @Query(value = """
                SELECT p.id, p.slug, p.cover_image_url, p.featured, p.status, p.created_at,
                       t.language, t.title
                FROM journal_posts p
                JOIN journal_post_translations t ON p.id = t.journal_post_id
                WHERE p.deleted_at IS NOT NULL
                GROUP BY p.id, p.slug, p.cover_image_url, p.featured, p.status, p.created_at,
                         t.language, t.title
                ORDER BY p.created_at DESC, t.language
                """, nativeQuery = true)
        List<Object[]> findAllDeletedNative();

        /**
         * The trashed-post subset matching a search term, on title or slug.
         *
         * <p>An empty {@code search} matches every trashed post, which is what the controller
         * passes on an unfiltered page load — that is why this is the only list query the service
         * issues rather than a branch between "all" and "searched".
         */
        @Query(value = """
                SELECT p.id, p.slug, p.cover_image_url, p.featured, p.status, p.created_at,
                       t.language, t.title
                FROM journal_posts p
                JOIN journal_post_translations t ON p.id = t.journal_post_id
                WHERE p.deleted_at IS NOT NULL
                AND (COALESCE(:search, '') = '' OR LOWER(t.title) LIKE :search
                     OR LOWER(p.slug) LIKE :search)
                GROUP BY p.id, p.slug, p.cover_image_url, p.featured, p.status, p.created_at,
                         t.language, t.title
                ORDER BY p.created_at DESC, t.language
                """, nativeQuery = true)
        List<Object[]> findDeletedBySearchNative(@Param("search") String search);

        /**
         * One trashed post by id, for the restore path.
         *
         * <p>Native for the same {@code @SQLRestriction} reason as the list queries.
         */
        @Query(value = "SELECT p.* FROM journal_posts p WHERE p.id = :id AND p.deleted_at IS NOT NULL",
                nativeQuery = true)
        Optional<JournalPost> findDeletedById(@Param("id") Long id);
}
