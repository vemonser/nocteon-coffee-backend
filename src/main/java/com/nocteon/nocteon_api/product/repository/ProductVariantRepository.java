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
