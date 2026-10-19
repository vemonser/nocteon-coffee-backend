package com.nocteon.nocteon_api.farm.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.farm.entity.Farm;


public interface FarmRepository extends JpaRepository<Farm, Long> {

                        @Query("""
                                SELECT DISTINCT f
                                FROM Farm f
                                LEFT JOIN FETCH f.origin
                                LEFT JOIN f.translations t
                                WHERE t.language = :language
                                AND (COALESCE(:search, '') = ''
                                OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')))
                                AND (:originSlug IS NULL OR f.origin.slug = :originSlug)
                                """)
                        Page<Farm> findAllPublic(
                        @Param("language") String language,
                        @Param("search") String search,
                        @Param("originSlug") String originSlug,
                        Pageable pageable);

        @Query("""
                        SELECT DISTINCT f FROM Farm f
                        LEFT JOIN FETCH f.origin
                        LEFT JOIN f.translations t
                        WHERE (:search IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')))
                        AND (:originSlug IS NULL OR f.origin.slug = :originSlug)
                        """)
        Page<Farm> findAllDashboard(
                        @Param("search") String search,
                        @Param("originSlug") String originSlug,
                        Pageable pageable);

        @Query("SELECT f FROM Farm f LEFT JOIN FETCH f.translations WHERE f.slug = :slug")
        Optional<Farm> findBySlugWithTranslations(@Param("slug") String slug);

        @Query("SELECT f FROM Farm f LEFT JOIN FETCH f.translations t WHERE f.slug = :slug AND t.language = :language")
        Optional<Farm> findBySlugAndLanguage(@Param("slug") String slug, @Param("language") String language);

        Optional<Farm> findBySlug(@Param("slug") String slug);

        boolean existsBySlug(String slug);

        @Query(value = """
                SELECT e.id, e.slug, e.image_url, e.created_at, o.slug as origin_slug,
                       et.language, et.name, et.country, et.description
                FROM farms e
                JOIN origins o ON e.origin_id = o.id
                JOIN farm_translations et ON e.id = et.farm_id
                WHERE e.deleted_at IS NOT NULL
                GROUP BY e.id, e.slug, e.image_url, e.created_at, o.slug, et.language, et.name, et.country, et.description
                """, nativeQuery = true)
        List<Object[]> findAllDeletedNative();

        @Query(value = """
                SELECT e.id, e.slug, e.image_url, e.created_at, o.slug as origin_slug,
                       et.language, et.name, et.country, et.description
                FROM farms e
                JOIN origins o ON e.origin_id = o.id
                JOIN farm_translations et ON e.id = et.farm_id
                WHERE e.deleted_at IS NOT NULL
                AND (LOWER(et.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(e.slug) LIKE LOWER(CONCAT('%', :search, '%')))
                GROUP BY e.id, e.slug, e.image_url, e.created_at, o.slug, et.language, et.name, et.country, et.description
                """, nativeQuery = true)
        List<Object[]> findDeletedBySearchNative(@Param("search") String search);

        @Query(value = "SELECT e.* FROM farms e WHERE e.id = :id AND e.deleted_at IS NOT NULL", nativeQuery = true)
        Optional<Farm> findDeletedById(@Param("id") Long id);
}