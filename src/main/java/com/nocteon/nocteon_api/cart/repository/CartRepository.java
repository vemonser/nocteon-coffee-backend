package com.nocteon.nocteon_api.cart.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query("""
                SELECT c FROM Cart c
                LEFT JOIN FETCH c.user u
                LEFT JOIN FETCH u.profile p
                WHERE (:search IS NULL OR :search = ''
                    OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(p.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', :search, '%')))
                AND (:hasItems IS NULL
                    OR (:hasItems = true AND SIZE(c.items) > 0)
                    OR (:hasItems = false AND SIZE(c.items) = 0))
                ORDER BY c.updatedAt DESC
            """)
    Page<Cart> findAllForDashboard(
            @Param("search") String search,
            @Param("hasItems") Boolean hasItems,
            Pageable pageable);
}
