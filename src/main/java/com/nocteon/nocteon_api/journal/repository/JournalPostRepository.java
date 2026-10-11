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

        @Query("""
                        SELECT DISTINCT p FROM JournalPost p
                        LEFT JOIN FETCH p.translations
                        LEFT JOIN FETCH p.products
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

        @Query("""
                        SELECT DISTINCT p FROM JournalPost p
                        LEFT JOIN FETCH p.translations t
                        LEFT JOIN FETCH p.products
                        LEFT JOIN FETCH p.author
                        WHERE p.slug = :slug
                        AND t.language = :language
                        """)
        Optional<JournalPost> findBySlugAndLanguage(
                        @Param("slug") String slug,
                        @Param("language") String language);

        boolean existsBySlug(String slug);
}
