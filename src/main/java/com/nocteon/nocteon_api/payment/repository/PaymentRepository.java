package com.nocteon.nocteon_api.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.payment.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderId(Long orderId);
    Optional<Payment> findByProviderPaymentId(String providerPaymentId);
    Optional<Payment> findByProviderOrderId(String providerOrderId);
}