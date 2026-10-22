package com.nocteon.nocteon_api.cart.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.cart.entity.Cart;


public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByUserId(Long userId);

    @Query("""
                SELECT c FROM Cart c
                WHERE SIZE(c.items) > 0
                AND c.lastActivityAt < :inactivityThreshold
                AND (c.lastRemindedAt IS NULL OR c.lastRemindedAt < :reminderCooldown)
            """)
    List<Cart> findAbandonedCarts(
            @Param("inactivityThreshold") Instant inactivityThreshold,
            @Param("reminderCooldown") Instant reminderCooldown);

    @Query("""
                SELECT c FROM Cart c
                LEFT JOIN FETCH c.user u
                LEFT JOIN FETCH u.profile p
                WHERE (:search IS NULL OR COALESCE(:search, '') = ''
                    OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(p.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', :search, '%')))
                AND (:hasItems IS NULL
                    OR (:hasItems = true AND SIZE(c.items) > 0)
                    OR (:hasItems = false AND SIZE(c.items) = 0))
                AND (:status IS NULL OR c.status = :status)
                AND (:from IS NULL OR c.lastActivityAt >= :from)
                AND (:to IS NULL OR c.lastActivityAt <= :to)
            """)
    Page<Cart> findAllForDashboard(
            @Param("search") String search,
            @Param("hasItems") Boolean hasItems,
            @Param("status") String status,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    @Query("SELECT COUNT(c) FROM Cart c WHERE c.status = :status")
    long countByStatus(@Param("status") String status);

    @Query("""
                SELECT COALESCE(SUM(pv.price * ci.quantity), 0)
                FROM Cart c
                JOIN c.items ci
                JOIN ci.productVariant pv
                WHERE c.status IN :statuses
                AND pv.price IS NOT NULL
            """)
    BigDecimal sumSubtotalsByStatuses(@Param("statuses") List<String> statuses);
}