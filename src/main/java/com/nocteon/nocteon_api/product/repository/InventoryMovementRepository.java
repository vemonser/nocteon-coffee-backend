package com.nocteon.nocteon_api.product.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.product.entity.InventoryMovement;
import com.nocteon.nocteon_api.product.enums.InventoryMovementReason;
import com.nocteon.nocteon_api.product.enums.InventoryMovementSource;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    /**
     * The last movements of one variant, newest first.
     *
     * <p>Sorted by {@code createdAt DESC, id DESC}: the id breaks a tie inside the
     * same millisecond, so two rows written by one checkout still read in a
     * stable order. Served by {@code idx_inventory_movements_variant_created}
     * (V21), which is why the sort keys are in that order.
     */
    List<InventoryMovement> findByVariantIdOrderByCreatedAtDescIdDesc(Long variantId, Pageable pageable);

    /** How many rows a variant has — used by the tests and by nothing else yet. */
    long countByVariantId(Long variantId);

    /**
     * The units one order took off the shelf, per variant — the negative
     * deltas of its {@code ORDER} rows, summed.
     *
     * <p>One query for a whole cancellation, and the only honest answer to
     * "did this order consume stock?". The payment method would guess; a
     * consumption that found nothing wrote no row, so it is simply absent
     * here.
     */
    @Query("""
            SELECT m.variantId, SUM(m.delta)
            FROM InventoryMovement m
            WHERE m.sourceType = :sourceType
              AND m.sourceId = :sourceId
              AND m.reason = :reason
              AND m.delta < 0
            GROUP BY m.variantId
            ORDER BY m.variantId
            """)
    List<Object[]> sumConsumedByOrder(
            @Param("sourceType") InventoryMovementSource sourceType,
            @Param("sourceId") Long sourceId,
            @Param("reason") InventoryMovementReason reason);
}
