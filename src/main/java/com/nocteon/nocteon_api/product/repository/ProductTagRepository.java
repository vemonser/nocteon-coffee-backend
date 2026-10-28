package com.nocteon.nocteon_api.product.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.product.entity.ProductTag;

public interface ProductTagRepository extends JpaRepository<ProductTag, Long> {

    Optional<ProductTag> findBySlug(String slug);

    List<ProductTag> findAllBySlugIn(Collection<String> slugs);

    /**
     * Every live tag with its names in one statement. The admin page shows both languages, so a
     * lazy per-row load would be an N+1 on a list that is small but opened constantly.
     */
    @Query("SELECT DISTINCT t FROM ProductTag t LEFT JOIN FETCH t.translations ORDER BY t.slug")
    List<ProductTag> findAllWithTranslations();

    /**
     * The slug column is unique across trashed rows too, so uniqueness has to include them — a
     * derived {@code existsBySlug} would not see a soft-deleted row and the insert would fail on
     * the constraint instead of generating the next free slug.
     */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM product_tags WHERE slug = :slug)", nativeQuery = true)
    boolean existsBySlugIncludingDeleted(@Param("slug") String slug);

    /**
     * How many products point at this tag. Counts trashed products as well: the link survives a
     * product's soft delete, so deleting the tag would leave a restored product without one.
     */
    @Query(value = "SELECT COUNT(DISTINCT l.product_id) FROM product_tag_links l WHERE l.tag_id = :tagId", nativeQuery = true)
    long countLinkedProducts(@Param("tagId") Long tagId);
}
