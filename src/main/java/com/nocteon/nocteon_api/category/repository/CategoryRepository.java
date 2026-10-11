package com.nocteon.nocteon_api.category.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.category.entity.Category;


public interface CategoryRepository extends JpaRepository<Category, Long> {
        @Query("""
                            SELECT DISTINCT c FROM Category c
                            JOIN c.translations t
                            WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :query, '%'))
                               OR LOWER(c.slug) LIKE LOWER(CONCAT('%', :query, '%'))
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
                        SELECT COUNT(DISTINCT c) FROM Category c JOIN c.translations t WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(c.slug) LIKE LOWER(CONCAT('%', :query, '%'))
                        """)
        long countSearchCategories(@Param("query") String query);

     @Query("""
               SELECT DISTINCT c
               FROM Category c
               LEFT JOIN c.translations t
               WHERE t.language = :language
               AND c.isActive = true
               AND (COALESCE(:search, '') = ''
                    OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))
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
                      OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))
                      OR LOWER(c.slug) LIKE LOWER(CONCAT('%', :search, '%'))
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

     boolean existsBySlug(String slug);

     List<Category> findBySlugIn(List<String> slugs);

}
