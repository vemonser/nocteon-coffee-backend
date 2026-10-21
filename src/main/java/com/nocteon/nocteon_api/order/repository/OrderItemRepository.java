package com.nocteon.nocteon_api.order.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.order.entity.OrderItem;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;
import com.nocteon.nocteon_api.product.enums.ProductType;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("""
                SELECT COALESCE(SUM(oi.quantity * ov.numericValue), 0)
                FROM OrderItem oi
                JOIN oi.order o
                JOIN oi.productVariant pv
                JOIN pv.product p
                JOIN pv.optionValues ov
                JOIN ov.option opt
                WHERE o.paymentStatus = :paymentStatus
                AND o.createdAt >= :since
                AND p.productType = :productType
                AND opt.slug = 'weight'
                AND ov.numericValue IS NOT NULL
            """)
    BigDecimal getTotalCoffeeSoldGrams(
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("since") Instant since,
            @Param("productType") ProductType productType);

    @Query("""
                SELECT c.slug, SUM(oi.totalPrice)
                FROM OrderItem oi
                JOIN oi.order o
                JOIN oi.productVariant pv
                JOIN pv.product p
                JOIN p.category c
                WHERE o.paymentStatus = :paymentStatus
                AND o.createdAt >= :since
                GROUP BY c.slug
                ORDER BY SUM(oi.totalPrice) DESC
            """)
    List<Object[]> getRevenueByCategory(@Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("since") Instant since);

    @Query("""
                SELECT origin.slug, SUM(oi.totalPrice)
                FROM OrderItem oi
                JOIN oi.order ord
                JOIN oi.productVariant pv
                JOIN pv.product p
                LEFT JOIN p.origin origin
                WHERE ord.paymentStatus = :paymentStatus
                AND ord.createdAt >= :since
                AND origin IS NOT NULL
                GROUP BY origin.slug
                ORDER BY SUM(oi.totalPrice) DESC
            """)
    List<Object[]> getRevenueByOrigin(@Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("since") Instant since);

    @Query("""
                SELECT rl.slug, SUM(oi.totalPrice)
                FROM OrderItem oi
                JOIN oi.order ord
                JOIN oi.productVariant pv
                JOIN pv.product p
                LEFT JOIN p.coffeeDetails cd
                LEFT JOIN cd.roastLevel rl
                WHERE ord.paymentStatus = :paymentStatus
                AND ord.createdAt >= :since
                AND rl IS NOT NULL
                GROUP BY rl.slug
                ORDER BY SUM(oi.totalPrice) DESC
            """)
    List<Object[]> getRevenueByRoastLevel(@Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("since") Instant since);

    @Query("""
                SELECT p.id, p.slug, SUM(oi.quantity)
                FROM OrderItem oi
                JOIN oi.order o
                JOIN oi.productVariant pv
                JOIN pv.product p
                WHERE o.paymentStatus = :paymentStatus
                AND o.createdAt >= :since
                GROUP BY p.id, p.slug
                ORDER BY SUM(oi.quantity) DESC
            """)
    List<Object[]> getTopSellingProductIds(
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("since") Instant since,
            Pageable pageable);
}