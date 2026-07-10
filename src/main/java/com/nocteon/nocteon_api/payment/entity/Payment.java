package com.nocteon.nocteon_api.payment.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.nocteon.nocteon_api.common.entity.BaseEntity;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@Table(name = "payments")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Payment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    @Builder.Default
    private String provider = "PAYMOB";

    @Column(name = "provider_payment_id")
    private String providerPaymentId;

    @Column(name = "provider_order_id")
    private String providerOrderId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    @Builder.Default
    private String currency = "EGP";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;
    
    @Column(name = "attempt_number")
    @Builder.Default
    private Integer attemptNumber  = 1;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "paid_at")
    private Instant paidAt;
}