package com.nocteon.nocteon_api.order.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.nocteon.nocteon_api.payment.entity.Payment;

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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A refund: money that was given back.
 *
 * <p>{@code payment_status = REFUNDED} on the order is only a FLAG: it says
 * THAT a refund happened, never HOW MUCH. The net paid of an order cannot be
 * computed without the amount, so the amount lives here.
 *
 * <p>{@code gatewayReference} is null while the refund is a MANUAL one: the
 * store returns the money outside the gateway, and the row records that it
 * is still owed. The gateway call is not implemented (the contract in
 * ORDERS_CONTRACT.md names it as a gap).
 */
@Entity
@Table(name = "order_refunds")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRefund {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "order_id", nullable = false)
        private Order order;

        /** The payment the money came back from, when it is known. */
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "payment_id")
        private Payment payment;

        @Column(name = "amount", nullable = false, precision = 10, scale = 2)
        private BigDecimal amount;

        @Column(name = "reason", length = 500)
        private String reason;

        @Column(name = "created_by")
        private String createdBy;

        /** The gateway's own reference — null while the refund is manual. */
        @Column(name = "gateway_reference")
        private String gatewayReference;

        @CreatedDate
        @Column(name = "created_at", updatable = false, nullable = false)
        private Instant createdAt;

        public OrderRefund(Order order, Payment payment, BigDecimal amount, String reason, String createdBy,
                        String gatewayReference) {
                this.order = order;
                this.payment = payment;
                this.amount = amount;
                this.reason = reason;
                this.createdBy = createdBy;
                this.gatewayReference = gatewayReference;
        }

        /** True while the money has NOT gone back through the gateway. */
        public boolean isManual() {
                return gatewayReference == null || gatewayReference.isBlank();
        }
}
