package com.nocteon.nocteon_api.journal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.journal.entity.JournalCategory;


public interface JournalCategoryRepository extends JpaRepository<JournalCategory, Long> {

    @Query("""
            SELECT DISTINCT c FROM JournalCategory c
            LEFT JOIN FETCH c.translations t
            WHERE t.language = :language
            """)
    List<JournalCategory> findAllWithLanguage(@Param("language") String language);

    /** Every live category with all of its translations, for the post form's category picker. */
    @Query("SELECT DISTINCT c FROM JournalCategory c LEFT JOIN FETCH c.translations ORDER BY c.id")
    List<JournalCategory> findAllWithTranslations();

    @Query("SELECT c FROM JournalCategory c LEFT JOIN FETCH c.translations WHERE c.slug = :slug")
    Optional<JournalCategory> findBySlugWithTranslations(@Param("slug") String slug);

    Optional<JournalCategory> findBySlug(String slug);
    boolean existsBySlug(String slug);

    /**
     * Trashed categories for the dashboard's deleted-items table.
     *
     * <p>Native because {@code @SQLRestriction} on {@code SoftDeletableEntity} filters
     * {@code deleted_at IS NOT NULL} out of every JPQL and derived query. Categories carry only
     * slug and translated names, so that is all this projects.
     */
    @Query(value = """
            SELECT c.id, c.slug, c.created_at, t.language, t.name
            FROM journal_categories c
            JOIN journal_category_translations t ON c.id = t.journal_category_id
            WHERE c.deleted_at IS NOT NULL
            GROUP BY c.id, c.slug, c.created_at, t.language, t.name
            ORDER BY c.created_at DESC, t.language
            """, nativeQuery = true)
    List<Object[]> findAllDeletedNative();

    /**
     * The trashed-category subset matching a search term, on name or slug.
     *
     * <p>An empty {@code search} matches every trashed category, which is why the service issues
     * only this query rather than branching between "all" and "searched".
     */
    @Query(value = """
            SELECT c.id, c.slug, c.created_at, t.language, t.name
            FROM journal_categories c
            JOIN journal_category_translations t ON c.id = t.journal_category_id
            WHERE c.deleted_at IS NOT NULL
            AND (COALESCE(:search, '') = '' OR LOWER(t.name) LIKE :search
                 OR LOWER(c.slug) LIKE :search)
            GROUP BY c.id, c.slug, c.created_at, t.language, t.name
            ORDER BY c.created_at DESC, t.language
            """, nativeQuery = true)
    List<Object[]> findDeletedBySearchNative(@Param("search") String search);

    /**
     * One trashed category by id, for the restore path.
     */
    @Query(value = "SELECT c.* FROM journal_categories c WHERE c.id = :id AND c.deleted_at IS NOT NULL",
            nativeQuery = true)
    Optional<JournalCategory> findDeletedById(@Param("id") Long id);
}