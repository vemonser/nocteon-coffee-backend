package com.nocteon.nocteon_api.cart.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.cart.entity.Cart;

import io.lettuce.core.dynamic.annotation.Param;

public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByUserId(Long userId);

    @Query("""
                SELECT c FROM Cart c
                WHERE SIZE(c.items) > 0
                AND c.updatedAt < :inactivityThreshold
                AND (c.lastRemindedAt IS NULL OR c.lastRemindedAt < :reminderCooldown)
            """)
    List<Cart> findAbandonedCarts(
            @Param("inactivityThreshold") Instant inactivityThreshold,
            @Param("reminderCooldown") Instant reminderCooldown);
}
