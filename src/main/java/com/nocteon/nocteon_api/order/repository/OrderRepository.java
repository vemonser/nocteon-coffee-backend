package com.nocteon.nocteon_api.order.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;

import io.lettuce.core.dynamic.annotation.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

        @Query("""
                            SELECT o FROM Order o
                            JOIN o.user u
                            WHERE CAST(o.id AS string) LIKE CONCAT('%', :query, '%')
                            OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))
                        """)
        List<Order> searchOrders(@Param("query") String query, Pageable pageable);

        @Query("SELECT COALESCE(SUM(o.totalAmount), 0), COUNT(o) FROM Order o " +
                        "WHERE o.paymentStatus = :status AND o.createdAt >= :since")
        List<Object[]> getRevenueStats(@Param("status") PaymentStatus status, @Param("since") Instant since);

        @Query("SELECT CAST(o.createdAt AS date), SUM(o.totalAmount) FROM Order o " +
                        "WHERE o.paymentStatus = :status AND o.createdAt >= :since " +
                        "GROUP BY CAST(o.createdAt AS date) ORDER BY CAST(o.createdAt AS date)")
        List<Object[]> getRevenueByDay(@Param("status") PaymentStatus status, @Param("since") Instant since);

        @Query("SELECT o.status, COUNT(o) FROM Order o WHERE o.createdAt >= :since GROUP BY o.status")
        List<Object[]> getOrdersByStatus(@Param("since") Instant since);

        Optional<Order> findByIdAndUserId(Long id, Long userId);

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