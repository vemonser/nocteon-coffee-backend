package com.nocteon.nocteon_api.payment.repository;

import com.nocteon.nocteon_api.payment.entity.Payment;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;



public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    Optional<Payment> findByProviderOrderId(String providerOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.providerOrderId = :providerOrderId")
    Optional<Payment> findByProviderOrderIdForUpdate(@Param("providerOrderId") String providerOrderId);

    Optional<Payment> findFirstByOrderIdAndStatusOrderByPaidAtDesc(Long orderId, PaymentStatus status);

    int countByOrderId(Long orderId);
}