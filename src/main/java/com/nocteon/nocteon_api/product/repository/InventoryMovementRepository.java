package com.nocteon.nocteon_api.product.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.product.entity.InventoryMovement;

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
}
