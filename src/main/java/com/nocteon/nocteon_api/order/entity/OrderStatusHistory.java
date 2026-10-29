package com.nocteon.nocteon_api.order.entity;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.nocteon.nocteon_api.order.enums.OrderStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One step of an order's status history.
 *
 * <p>The row is written in the SAME TRANSACTION as the status change it
 * describes, so an order can never carry a status that its history does not
 * explain. A status change without its history row is a bug, not a partial
 * success.
 *
 * <p>{@code fromStatus} is null for the first row: the order was PLACED, it
 * came from nowhere.
 */
@Entity
@Table(name = "order_status_history")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusHistory {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "order_id", nullable = false)
        private Order order;

        @Column(name = "from_status")
        private String fromStatus;

        @Column(name = "to_status", nullable = false)
        private String toStatus;

        @Column(name = "note", columnDefinition = "TEXT")
        private String note;

        /** Who moved it: the e-mail of the admin, or the system. */
        @Column(name = "changed_by")
        private String changedBy;

        /** Whether the customer was asked to be told about the change. */
        @Column(name = "notify_customer", nullable = false)
        private boolean notifyCustomer = false;

        /**
         * The kind of row when it is NOT a status change (FE-20).
         *
         * <p>Null for a status change, which is the overwhelming majority of
         * rows. A return writes {@code RETURN_REQUESTED},
         * {@code RETURN_APPROVED} or {@code RETURN_REJECTED} here and leaves
         * {@code fromStatus} null, because nothing was transitioned; the
         * timeline draws it from this column and the status CHAIN ignores it.
         */
        @Column(name = "event_type", length = 32)
        private String eventType;

        @CreatedDate
        @Column(name = "created_at", updatable = false, nullable = false)
        private Instant createdAt;

        public OrderStatusHistory(Order order, OrderStatus fromStatus, OrderStatus toStatus, String note,
                        String changedBy, boolean notifyCustomer) {
                this.order = order;
                this.fromStatus = fromStatus != null ? fromStatus.name() : null;
                this.toStatus = toStatus.name();
                this.note = note;
                this.changedBy = changedBy;
                this.notifyCustomer = notifyCustomer;
        }

        /**
         * An order EVENT that is not a status change (FE-20).
         *
         * <p>{@code toStatus} is the order's status AT THE TIME: the row stays a
         * true statement about the order, and {@code eventType} is what says
         * which event it was. {@code fromStatus} is null — nothing transitioned.
         */
        public OrderStatusHistory(Order order, OrderStatus currentStatus, String eventType, String note,
                        String changedBy) {
                this.order = order;
                this.fromStatus = null;
                this.toStatus = currentStatus != null ? currentStatus.name() : OrderStatus.PENDING.name();
                this.eventType = eventType;
                this.note = note;
                this.changedBy = changedBy;
                this.notifyCustomer = false;
        }
}
