package com.nocteon.nocteon_api.order.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.nocteon.nocteon_api.common.entity.BaseEntity;
import com.nocteon.nocteon_api.order.enums.ReturnStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A return: goods that came back from a customer (FE-20, migration V22).
 *
 * <p>Separate from {@link OrderRefund} on purpose. A refund is MONEY, a return
 * is GOODS: the store can give one back without the other (a return with no
 * money back is an exchange; money back with no goods returned is a goodwill
 * refund). {@code refund} is the link between them WHEN one exists, and it is
 * null when it does not — never a fake row.
 *
 * <p>Approving a return is the only state change that moves stock: it restocks
 * every line through {@code InventoryMovementService} (reason RETURN, source
 * RETURN, {@code source_id} = this row's id), so the FE-13 log stays the single
 * record of what stock did and why. Rejecting restocks nothing.
 *
 * <p>The decision fields are filled together and cannot be half-filled: the
 * table has a CHECK for it, so a REQUESTED row with a decided_at (or an
 * APPROVED one without) is refused by the database, not only by the service.
 */
@Entity
@SuperBuilder
@Table(name = "returns")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class OrderReturn extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ReturnStatus status = ReturnStatus.REQUESTED;

    /** Why the goods came back. Required: it is the answer nobody can give later. */
    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    /** Who recorded it: the admin's e-mail. */
    @Column(name = "requested_by", nullable = false, length = 255)
    private String requestedBy;

    /** Who decided it, once somebody did. */
    @Column(name = "decided_by", length = 255)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    /** The refund that pays this return back, when the money was returned. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_id")
    private OrderRefund refund;

    @OneToMany(mappedBy = "orderReturn", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @lombok.Builder.Default
    private List<ReturnItem> items = new ArrayList<>();

    /** True while awaiting a decision. Only this state can be decided. */
    public boolean isRequested() {
        return status == ReturnStatus.REQUESTED;
    }
}
