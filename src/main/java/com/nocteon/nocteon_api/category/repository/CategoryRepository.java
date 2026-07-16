package com.nocteon.nocteon_api.category.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.category.entity.Category;

import io.lettuce.core.dynamic.annotation.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {
        @Query("""
                            SELECT DISTINCT c FROM Category c
                            JOIN c.translations t
                            WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :query, '%'))
                               OR LOWER(c.slug) LIKE LOWER(CONCAT('%', :query, '%'))
                        """)
        List<Category> searchCategories(@Param("query") String query, Pageable pageable);

     @Query("""
               SELECT DISTINCT c
               FROM Category c
               LEFT JOIN c.translations t
               WHERE t.language = :language
               AND c.isActive = true
               AND (:search IS NULL OR
                    LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')))
               """)
     Page<Category> findAllPublic(
               @Param("language") String language,
               @Param("search") String search,
               Pageable pageable);

     @Query("""
               SELECT DISTINCT c FROM Category c
               LEFT JOIN c.translations t
               WHERE (:search = ''
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
