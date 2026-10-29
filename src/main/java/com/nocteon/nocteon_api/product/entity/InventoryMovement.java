package com.nocteon.nocteon_api.product.entity;

import com.nocteon.nocteon_api.common.entity.BaseEntity;
import com.nocteon.nocteon_api.product.enums.InventoryMovementReason;
import com.nocteon.nocteon_api.product.enums.InventoryMovementSource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * One line of a variant's stock history (FE-13, migration V21).
 *
 * <p>Immutable in practice: rows are written and read, never updated. The row
 * carries the COUNT BEFORE and AFTER instead of only the delta, so the log can
 * be checked against itself (the table's CHECK constraint does exactly that) and
 * a reconciliation reads the numbers somebody actually saw.
 *
 * <p>{@code variantId} is a plain id, not a relation: a soft-deleted variant
 * keeps its rows, and loading a trashed variant to print a movement would drag
 * the whole product graph in for one number.
 */
@Entity
@SuperBuilder
@Table(name = "inventory_movements")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class InventoryMovement extends BaseEntity {

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "stock_before", nullable = false)
    private int stockBefore;

    @Column(name = "stock_after", nullable = false)
    private int stockAfter;

    /** {@code stockAfter - stockBefore}: negative when units left the shelf. */
    @Column(name = "delta", nullable = false)
    private int delta;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 32)
    private InventoryMovementReason reason;

    /** The admin's sentence for a manual adjustment; null for an order's row. */
    @Column(name = "reason_note", length = 500)
    private String reasonNote;

    /** Who did it: the admin's e-mail, the customer's, or a system worker. */
    @Column(name = "actor", nullable = false, length = 255)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private InventoryMovementSource sourceType;

    /** {@code orders.id} / {@code products.id} for the source; null for MANUAL. */
    @Column(name = "source_id")
    private Long sourceId;
}
