package com.nocteon.nocteon_api.category.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.category.entity.CategoryTranslation;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryTranslationRepository extends JpaRepository<CategoryTranslation, Long> {
    Optional<CategoryTranslation> findByCategoryIdAndLanguage(Long categoryId, String language);

    List<CategoryTranslation> findByCategoryId(Long id);
    void deleteByCategoryId(Long categoryId);

    /**
     * Whether a LIVE (non-soft-deleted) category already has a translation with the given
     * (language, name) (case-insensitive). {@code excludeCategoryId} may be null to consider
     * all categories (create path); on update it is the current category's id so a no-op rename
     * does not collide with itself.
     *
     * <p>Explicitly filters {@code c.deletedAt IS NULL} rather than relying on Hibernate's
     * {@code @SQLRestriction} (which is defined on the parent entity and is not applied to
     * hand-written JPQL joins in all cases).
     */
    @Query(value = """
            SELECT (COUNT(*) > 0)
            FROM category_translations ct
            JOIN categories c ON c.id = ct.category_id
            WHERE ct.language = :language
              AND LOWER(REGEXP_REPLACE(ct.name, '^[ \\t\\n\\r\\f\\v]+|[ \\t\\n\\r\\f\\v]+$', '', 'g')) =
                  LOWER(REGEXP_REPLACE(:name, '^[ \\t\\n\\r\\f\\v]+|[ \\t\\n\\r\\f\\v]+$', '', 'g'))
              AND c.deleted_at IS NULL
              AND (:excludeCategoryId IS NULL OR c.id <> :excludeCategoryId)
            """, nativeQuery = true)
    boolean existsLiveTranslationName(@Param("language") String language,
                                     @Param("name") String name,
                                     @Param("excludeCategoryId") Long excludeCategoryId);
}
