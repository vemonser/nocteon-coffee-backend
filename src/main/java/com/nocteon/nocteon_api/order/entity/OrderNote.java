package com.nocteon.nocteon_api.order.entity;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

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
 * An internal note about an order.
 *
 * <p>NOT the customer's note: {@code orders.notes} is what the customer
 * wrote ("please leave it at the door"). This is what the team wrote about
 * the order, and the dashboard's order page shows it to the team only.
 */
@Entity
@Table(name = "order_notes")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderNote {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "order_id", nullable = false)
        private Order order;

        /** Who wrote it. */
        @Column(name = "author", nullable = false)
        private String author;

        @Column(name = "body", nullable = false, columnDefinition = "TEXT")
        private String body;

        /** Always true today — a note that is not internal is a message to the customer. */
        @Column(name = "is_internal", nullable = false)
        private boolean internal = true;

        @CreatedDate
        @Column(name = "created_at", updatable = false, nullable = false)
        private Instant createdAt;

        public OrderNote(Order order, String author, String body, boolean internal) {
                this.order = order;
                this.author = author;
                this.body = body;
                this.internal = internal;
        }
}
