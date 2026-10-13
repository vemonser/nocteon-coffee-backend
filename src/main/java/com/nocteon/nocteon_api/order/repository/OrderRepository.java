package com.nocteon.nocteon_api.order.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;


public interface OrderRepository extends JpaRepository<Order, Long> {

        @Query("""
                        SELECT o FROM Order o
                        JOIN o.user u
                        WHERE CAST(o.id AS string) LIKE CONCAT('%', :query, '%')
                           OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))
                        ORDER BY CASE
                                WHEN CAST(o.id AS string) = :query THEN 0
                                WHEN CAST(o.id AS string) LIKE CONCAT(:query, '%') THEN 1
                                WHEN LOWER(u.email) = LOWER(:query) THEN 2
                                ELSE 3
                            END,
                            o.id
                        """)
                        /**
         * A whole order number first, then a prefix of one, then an email match.
         *
         * <p>Orders are usually searched by the number a customer quotes, so matching the id
         * exactly outranks an email that merely contains the digits. No GROUP BY is needed here:
         * the join is many-to-one, so it cannot duplicate rows.
         */
List<Order> searchOrders(@Param("query") String query, Pageable pageable);

        /**
         * How many rows the search query matches, uncapped.
         *
         * <p>The search takes a fixed-size page of rows, so the returned list cannot be used
         * to report a total: it is the page size, not the match count. This mirrors the row
         * query's predicates exactly so the two cannot drift apart.
         */
        @Query("""
                        SELECT COUNT(o) FROM Order o JOIN o.user u WHERE CAST(o.id AS string) LIKE CONCAT('%', :query, '%') OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))
                        """)
        long countSearchOrders(@Param("query") String query);

        @Query("SELECT COALESCE(SUM(o.totalAmount), 0), COUNT(o) FROM Order o " +
                        "WHERE o.paymentStatus = :status AND o.createdAt >= :since")
        List<Object[]> getRevenueStats(@Param("status") PaymentStatus status, @Param("since") Instant since);

        @Query("SELECT CAST(o.createdAt AS date), SUM(o.totalAmount) FROM Order o " +
                        "WHERE o.paymentStatus = :status AND o.createdAt >= :since " +
                        "GROUP BY CAST(o.createdAt AS date) ORDER BY CAST(o.createdAt AS date)")
        List<Object[]> getRevenueByDay(@Param("status") PaymentStatus status, @Param("since") Instant since);

        @Query("SELECT o.status, COUNT(o) FROM Order o WHERE o.paymentStatus = :status AND o.createdAt >= :since GROUP BY o.status")
        List<Object[]> getOrdersByStatus(@Param("status") PaymentStatus status, @Param("since") Instant since);

        @Query("SELECT o.paymentMethod, COUNT(o) FROM Order o WHERE o.paymentStatus = :status AND o.createdAt >= :since GROUP BY o.paymentMethod")
        List<Object[]> getOrdersByPaymentMethod(@Param("status") PaymentStatus status, @Param("since") Instant since);

        @Query("SELECT COUNT(o) FROM Order o WHERE o.paymentStatus = :status AND o.createdAt >= :since")
        long countByPaymentStatusAndCreatedAtAfter(@Param("status") PaymentStatus status, @Param("since") Instant since);

        Optional<Order> findByIdAndUserId(Long id, Long userId);

        /**
         * Checkouts whose payment callback never arrived: still awaiting payment
         * and older than the cutoff. The expiry sweep cancels these and gives
         * their stock back to the shelf.
         */
        List<Order> findByStatusAndPaymentStatusAndCreatedAtBefore(
                        OrderStatus status, PaymentStatus paymentStatus, Instant cutoff);

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