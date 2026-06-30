package com.nocteon.nocteon_api.order.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.enums.OrderStatus;

import io.lettuce.core.dynamic.annotation.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("""
            SELECT DISTINCT o FROM Order o
            LEFT JOIN o.items i
            WHERE o.user.id = :userId
            ORDER BY o.createdAt DESC
            """)
    Page<Order> findByUserId(
            @Param("userId") Long userId,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT o FROM Order o
            WHERE (:status IS NULL OR o.status = :status)
            ORDER BY o.createdAt DESC
            """)
    Page<Order> findAllWithFilters(
            @Param("status") OrderStatus status,
            Pageable pageable);
}