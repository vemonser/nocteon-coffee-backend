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
     * Atomically reserves stock: one statement that both checks and decrements,
     * so two concurrent checkouts cannot sell the same unit twice. Returns the
     * number of rows taken (1 on success, 0 when someone else got there first).
     */
    @Modifying
    @Query("""
            UPDATE ProductVariant v
            SET v.stockQuantity = v.stockQuantity - :qty
            WHERE v.id = :id AND v.stockQuantity >= :qty
            """)
    int decrementStockIfAvailable(@Param("id") Long id, @Param("qty") int qty);

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
