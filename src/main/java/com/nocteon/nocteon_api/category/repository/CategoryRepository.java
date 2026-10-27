package com.nocteon.nocteon_api.category.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.category.dto.response.DashboardCategoryResponse;
import com.nocteon.nocteon_api.category.entity.Category;


public interface CategoryRepository extends JpaRepository<Category, Long> {
        /**
         * Rank tiers, weakest to strongest: 3 anywhere, 2 whole word, 1 prefix, 0 exact.
         *
         * <p>The dashboard caps each type at five rows, so without an explicit order the five it
         * keeps were whichever five the database returned first. That is not merely unstable, it
         * is unhelpful: an exact slug has to outrank a row that merely contains the same letters
         * somewhere in its name.
         *
         * <p>Ties break on the slug, which is language-neutral. Ordering by a translated name
         * would rank the same category differently depending on which translations happen to
         * exist, and could interleave two languages in one alphabetical sequence.
         *
         * <p>{@code GROUP BY} replaces {@code DISTINCT} here. PostgreSQL rejects an ORDER BY
         * expression under SELECT DISTINCT unless that expression is in the select list, and the
         * rank is a computed aggregate. Grouping by the primary key also satisfies Postgres'
         * functional-dependency rule, so selecting the whole entity stays legal.
         */
        @Query("""
                        SELECT c FROM Category c
                        JOIN c.translations t
                        WHERE LOWER(t.name) LIKE :query
                           OR LOWER(c.slug) LIKE :query
                        GROUP BY c.id, c.slug
                        ORDER BY MIN(CASE
                                WHEN LOWER(c.slug) = LOWER(:query) THEN 0
                                WHEN LOWER(t.name) = LOWER(:query) THEN 0
                                WHEN LOWER(c.slug) LIKE :query THEN 1
                                WHEN LOWER(t.name) LIKE :query THEN 1
                                WHEN LOWER(t.name) LIKE :query THEN 2
                                ELSE 3
                            END),
                            MIN(LOWER(c.slug))
                        """)
        List<Category> searchCategories(@Param("query") String query, Pageable pageable);

        /**
         * How many rows the search query matches, uncapped.
         *
         * <p>The search takes a fixed-size page of rows, so the returned list cannot be used
         * to report a total: it is the page size, not the match count. This mirrors the row
         * query's predicates exactly so the two cannot drift apart.
         */
        @Query("""
                        SELECT COUNT(DISTINCT c) FROM Category c JOIN c.translations t WHERE LOWER(t.name) LIKE :query OR LOWER(c.slug) LIKE :query
                        """)
        long countSearchCategories(@Param("query") String query);

     @Query("""
               SELECT DISTINCT c
               FROM Category c
               LEFT JOIN c.translations t
               WHERE t.language = :language
               AND c.isActive = true
               AND (COALESCE(:search, '') = ''
                    OR LOWER(t.name) LIKE :search
               )
               """)

     Page<Category> findAllPublic(
               @Param("language") String language,
               @Param("search") String search,
               Pageable pageable);

     @Query("""
               SELECT DISTINCT c FROM Category c
               LEFT JOIN c.translations t
               WHERE (COALESCE(:search, '') = ''
                      OR LOWER(t.name) LIKE :search
                      OR LOWER(c.slug) LIKE :search
                     )
                 AND (:isActive IS NULL
                      OR c.isActive = :isActive)
               """)
     Page<Category> findAllDashboard(
               @Param("search") String search,
               @Param("isActive") Boolean isActive,
               Pageable pageable);

     @Query("""
               SELECT c FROM Category c
               LEFT JOIN FETCH c.translations
               WHERE c.slug = :slug
               """)
     Optional<Category> findBySlugWithTranslations(@Param("slug") String slug);

     @Query("""
               SELECT c FROM Category c
               LEFT JOIN FETCH c.translations t
               WHERE c.slug = :slug
               AND t.language = :language
               """)
     Optional<Category> findBySlugAndLanguage(
               @Param("slug") String slug,
               @Param("language") String language);

     Optional<Category> findBySlug(String slug);

     /**
      * Slug lookup with translations fetched in the same query.
      *
      * <p>{@code open-in-view} is off, so a caller that reads
      * {@code category.getTranslations()} after the repository returns MUST
      * use this variant: the plain {@code findBySlugIn} leaves the collection
      * lazy and the first read throws LazyInitializationException.
      */
     @Query("""
               SELECT DISTINCT c FROM Category c
               LEFT JOIN FETCH c.translations
               WHERE c.slug IN :slugs
               """)
     List<Category> findBySlugInWithTranslations(@Param("slugs") Collection<String> slugs);

     boolean existsBySlug(String slug);

     @Query(value = "SELECT COUNT(*) > 0 FROM categories WHERE slug = :slug", nativeQuery = true)
     boolean existsBySlugIncludingDeleted(@Param("slug") String slug);

     List<Category> findBySlugIn(List<String> slugs);

        @Query(value = """
        SELECT c.id, c.slug, c.image_url, c.is_active, c.created_at,
               ct.language, ct.name, ct.description, c.deleted_at
        FROM categories c
        JOIN category_translations ct ON c.id = ct.category_id
        WHERE c.deleted_at IS NOT NULL
        GROUP BY c.id, c.slug, c.image_url, c.is_active, c.created_at, ct.language, ct.name, ct.description, c.deleted_at
        ORDER BY c.created_at DESC, ct.language
        """, nativeQuery = true)
        List<Object[]> findAllDeletedNative();

        @Query(value = """
        SELECT c.id, c.slug, c.image_url, c.is_active, c.created_at,
               ct.language, ct.name, ct.description, c.deleted_at
        FROM categories c
        JOIN category_translations ct ON c.id = ct.category_id
        WHERE c.deleted_at IS NOT NULL
        AND (COALESCE(:search, '') = '' OR LOWER(ct.name) LIKE :search OR LOWER(c.slug) LIKE :search)
        GROUP BY c.id, c.slug, c.image_url, c.is_active, c.created_at, ct.language, ct.name, ct.description, c.deleted_at
        ORDER BY c.created_at DESC, ct.language
        """, nativeQuery = true)
        List<Object[]> findDeletedBySearchNative(@Param("search") String search);

        @Query(value = "SELECT c.* FROM categories c WHERE c.id = :id AND c.deleted_at IS NOT NULL", nativeQuery = true)
        Optional<Category> findDeletedById(@Param("id") Long id);

}