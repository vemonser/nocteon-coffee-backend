package com.nocteon.nocteon_api.product.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.product.entity.ProductOptionValue;
import com.nocteon.nocteon_api.product.entity.ProductVariant;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    // في ProductVariantRepository
    // في ProductVariantRepository
    @Query("""
            SELECT DISTINCT ov
            FROM ProductVariant v
            JOIN v.optionValues ov
            WHERE v.product.id IN :productIds
            AND v.isActive = true
            """)
    List<ProductOptionValue> findDistinctOptionValuesByProductIds(@Param("productIds") List<Long> productIds);

    List<ProductVariant> findByProductId(Long productId);

    /**
     * Atomically reserves stock: checks available (stock - reserved) >= qty and
     * increments reservedQuantity. Returns 1 on success, 0 when insufficient.
     */
    @Modifying
    @Query("""
            UPDATE ProductVariant v
            SET v.reservedQuantity = v.reservedQuantity + :qty
            WHERE v.id = :id AND (v.stockQuantity - v.reservedQuantity) >= :qty
            """)
    int reserveStockIfAvailable(@Param("id") Long id, @Param("qty") int qty);

    /**
     * Releases previously reserved stock (on cancel/expiry). Clamped at 0.
     */
    @Modifying
    @Query("""
            UPDATE ProductVariant v
            SET v.reservedQuantity = CASE WHEN v.reservedQuantity >= :qty THEN v.reservedQuantity - :qty ELSE 0 END
            WHERE v.id = :id
            """)
    int releaseStock(@Param("id") Long id, @Param("qty") int qty);

    /**
     * Consumes reserved stock on fulfillment/payment: decrements both
     * stockQuantity and reservedQuantity. Returns 1 on success.
     */
    @Modifying
    @Query("""
            UPDATE ProductVariant v
            SET v.stockQuantity = v.stockQuantity - :qty,
                v.reservedQuantity = CASE WHEN v.reservedQuantity >= :qty THEN v.reservedQuantity - :qty ELSE 0 END
            WHERE v.id = :id AND v.stockQuantity >= :qty
            """)
    int consumeStock(@Param("id") Long id, @Param("qty") int qty);

    /**
     * Fresh stock read for the failure path: the managed entity may hold a
     * stale count after a bulk update, while this scalar query hits the
     * database and reports what is really left.
     */
    @Query("SELECT v.stockQuantity FROM ProductVariant v WHERE v.id = :id")
    int findStockById(@Param("id") Long id);

    @Modifying
    @Query("""
            UPDATE ProductVariant v
            SET v.stockQuantity = v.stockQuantity + :qty
            WHERE v.id = :id
            """)
    int incrementStock(@Param("id") Long id, @Param("qty") int qty);

    Optional<ProductVariant> findBySku(String sku);

    /**
     * FE-15: the id of the variant that already owns {@code barcode}, <em>including soft-deleted
     * rows</em>.
     *
     * <p>Native and unfiltered on purpose, for the same reason {@link #existsBySkuIncludingDeleted}
     * is: {@code @SQLRestriction} hides a trashed variant, but its row (and therefore its barcode)
     * is still in the column and still occupies the partial unique index. Answering "is this free"
     * from the filtered view would report it as free and then fail on the index with a 500.
     *
     * <p>The id comes back rather than a boolean so an update can exclude the variant that is
     * keeping its own barcode.
     */
    @Query(value = "SELECT v.id FROM product_variants v WHERE v.barcode = :barcode LIMIT 1", nativeQuery = true)
    Optional<Long> findIdByBarcodeIncludingDeleted(@Param("barcode") String barcode);

    /**
     * FE-15: the barcodes of every variant of these products, ordered, for the products CSV
     * export.
     *
     * <p>One query for a whole page instead of one product-detail read per row: the export needs
     * nothing else from the variant, so loading the entities (with their option values) just to
     * print one string would be the N+1 the rest of the catalogue list avoids. Trashed variants
     * are excluded by {@code @SQLRestriction}, which is what the dashboard list shows too.
     */
    @Query("""
            SELECT v.product.id, v.barcode
            FROM ProductVariant v
            WHERE v.product.id IN :productIds
              AND v.barcode IS NOT NULL
            ORDER BY v.product.id, v.id
            """)
    List<Object[]> findBarcodesByProductIds(@Param("productIds") List<Long> productIds);

    @Modifying
    @Query(value = "DELETE FROM variant_option_values WHERE option_value_id = :valueId", nativeQuery = true)
    void removeValueFromVariants(@Param("valueId") Long valueId);

    /**
     * Grouped count of distinct variants linked to each option value id.
     */
    @Query("""
            SELECT ov.id, COUNT(DISTINCT v.id)
            FROM ProductVariant v JOIN v.optionValues ov
            WHERE ov.id IN :valueIds
            GROUP BY ov.id
            """)
    List<Object[]> countVariantsByValueIds(@Param("valueIds") List<Long> valueIds);

    /**
     * Grouped count of distinct variants linked to each option id (via any of its values).
     */
    @Query("""
            SELECT ov.option.id, COUNT(DISTINCT v.id)
            FROM ProductVariant v JOIN v.optionValues ov
            WHERE ov.option.id IN :optionIds
            GROUP BY ov.option.id
            """)
    List<Object[]> countVariantsByOptionIds(@Param("optionIds") List<Long> optionIds);

    /**
     * Grouped aggregate per product for the catalog list: variant count, total stock,
     * low-stock variant count, and min/max price across enabled (active, non-deleted)
     * variants. One query for a whole page of products — no N+1.
     */
    @Query("""
            SELECT v.product.id,
                   COUNT(v),
                   COALESCE(SUM(v.stockQuantity), 0),
                   SUM(CASE WHEN v.stockQuantity <= v.reorderThreshold THEN 1 ELSE 0 END),
                   MIN(v.price),
                   MAX(v.price)
            FROM ProductVariant v
            WHERE v.product.id IN :productIds
              AND v.isActive = true
            GROUP BY v.product.id
            """)
    List<Object[]> findAggregatesByProductIds(@Param("productIds") List<Long> productIds);

    boolean existsBySku(String sku);

    /**
     * SKUs are unique across trashed variants too, and {@code existsBySku} cannot see them
     * ({@code @SQLRestriction}). FE-12 uses this so a copy never collides with a row it cannot
     * read.
     */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM product_variants WHERE sku = :sku)", nativeQuery = true)
    boolean existsBySkuIncludingDeleted(@Param("sku") String sku);

    void deleteByProductId(Long productId);

    /**
     * Every variant of a product, <em>including</em> soft-deleted ones.
     *
     * <p>Derived queries cannot serve this: {@code @SQLRestriction} on
     * {@code SoftDeletableEntity} hides {@code deleted_at IS NOT NULL} rows from them, and the
     * permanent-delete path must remove trashed variants too — otherwise the
     * {@code product_variants.product_id} foreign key rejects the parent's removal. Native and
     * deliberately unfiltered on {@code deleted_at}.
     */
    @Query(value = "SELECT v.* FROM product_variants v WHERE v.product_id = :productId", nativeQuery = true)
    List<ProductVariant> findAllByProductIdIncludingDeleted(@Param("productId") Long productId);
}
