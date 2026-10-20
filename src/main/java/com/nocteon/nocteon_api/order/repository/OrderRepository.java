package com.nocteon.nocteon_api.order.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.payment.enums.PaymentMethod;
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


        /**
         * The newest qualifying order per (product, user) for a set of users.
         *
         * <p>ONE query for a whole page of reviews — the dashboard would
         * otherwise run a query per row. A qualifying order is one that is
         * neither CANCELLED nor PENDING, which is the same rule the review's
         * verified flag was set with.
         *
         * <p>Each row is (productId, userId, orderId, orderNumber), newest
         * first; the caller keeps the FIRST row per (product, user) pair.
         */
        @Query("""
                SELECT i.productVariant.product.id, o.user.id, o.id, o.orderNumber
                FROM Order o
                JOIN o.items i
                WHERE o.user.id IN :userIds
                AND o.status NOT IN ('CANCELLED', 'PENDING')
                ORDER BY o.createdAt DESC, o.id DESC
                """)
        List<Object[]> findLatestQualifyingOrdersForUsers(
                @Param("userIds") Collection<Long> userIds);

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

        /**
         * The orders of the dashboard list, with EVERY filter.
         *
         * <p>The filters are the ones the list page offers: the order status,
         * the payment status, the payment method, a created-at range and a
         * free-text search over the order number, the id, the customer's
         * name, the e-mail and the phone.
         *
         * <p>An absent filter narrows nothing, so the counts behind the
         * cards and the rows in the table are always the same set.
         *
         * <p>The search is a ready-made PATTERN ("%term%"), lower-cased by the
         * caller: the columns are compared with LOWER(), and the pattern is
         * not, because LOWER() around a concatenation makes PostgreSQL infer
         * the parameter's type — and it picks bytea, which has no lower().
         *
         * <p>The date range is a pair of INSTANTS, not dates: the range is
         * half-open [from, to) so that a day is a whole calendar day in the
         * store's own time zone, and an order placed at 23:30 belongs to
         * that day.
         *
         * <p>The two bounds are NOT optional in the query. A parameter that
         * appears in a "is null" test has no type there, and PostgreSQL then
         * cannot infer the type of the parameter in the comparison next to
         * it ("could not determine data type of parameter"). The caller
         * passes a wide-open range when the filter is absent.
         */
        @Query("""
                        SELECT DISTINCT o FROM Order o
                        JOIN o.user u
                        LEFT JOIN o.address a
                        LEFT JOIN u.profile p
                        WHERE (:status IS NULL OR o.status = :status)
                          AND (:paymentStatus IS NULL OR o.paymentStatus = :paymentStatus)
                          AND (:paymentMethod IS NULL OR o.paymentMethod = :paymentMethod)
                          AND o.createdAt >= :dateFrom
                          AND o.createdAt < :dateTo
                          AND (
                                :search IS NULL
                                OR LOWER(o.orderNumber) LIKE :search
                                OR CAST(o.id AS string) LIKE :search
                                OR LOWER(u.email) LIKE :search
                                OR LOWER(p.firstName) LIKE :search
                                OR LOWER(p.lastName) LIKE :search
                                OR a.phone LIKE :search
                              )
                        """)
        Page<Order> findAllWithFilters(
                        @Param("status") OrderStatus status,
                        @Param("paymentStatus") PaymentStatus paymentStatus,
                        @Param("paymentMethod") PaymentMethod paymentMethod,
                        @Param("dateFrom") Instant dateFrom,
                        @Param("dateTo") Instant dateTo,
                        @Param("search") String searchPattern,
                        Pageable pageable);

        /**
         * How many orders the filter selects, and how many of them are in
         * each status.
         *
         * <p>The predicate is the SAME as the row query's, so the counts
         * behind the cards can never disagree with the rows in the table.
         */
        @Query("""
                        SELECT o.status, COUNT(o) FROM Order o
                        JOIN o.user u
                        LEFT JOIN o.address a
                        LEFT JOIN u.profile p
                        WHERE (:status IS NULL OR o.status = :status)
                          AND (:paymentStatus IS NULL OR o.paymentStatus = :paymentStatus)
                          AND (:paymentMethod IS NULL OR o.paymentMethod = :paymentMethod)
                          AND o.createdAt >= :dateFrom
                          AND o.createdAt < :dateTo
                          AND (
                                :search IS NULL
                                OR LOWER(o.orderNumber) LIKE :search
                                OR CAST(o.id AS string) LIKE :search
                                OR LOWER(u.email) LIKE :search
                                OR LOWER(p.firstName) LIKE :search
                                OR LOWER(p.lastName) LIKE :search
                                OR a.phone LIKE :search
                              )
                        GROUP BY o.status
                        """)
        List<Object[]> countByStatusWithFilters(
                        @Param("status") OrderStatus status,
                        @Param("paymentStatus") PaymentStatus paymentStatus,
                        @Param("paymentMethod") PaymentMethod paymentMethod,
                        @Param("dateFrom") Instant dateFrom,
                        @Param("dateTo") Instant dateTo,
                        @Param("search") String searchPattern);

        @Query("""
                        SELECT o.paymentStatus, COUNT(o) FROM Order o
                        JOIN o.user u
                        LEFT JOIN o.address a
                        LEFT JOIN u.profile p
                        WHERE (:status IS NULL OR o.status = :status)
                          AND (:paymentStatus IS NULL OR o.paymentStatus = :paymentStatus)
                          AND (:paymentMethod IS NULL OR o.paymentMethod = :paymentMethod)
                          AND o.createdAt >= :dateFrom
                          AND o.createdAt < :dateTo
                          AND (
                                :search IS NULL
                                OR LOWER(o.orderNumber) LIKE :search
                                OR CAST(o.id AS string) LIKE :search
                                OR LOWER(u.email) LIKE :search
                                OR LOWER(p.firstName) LIKE :search
                                OR LOWER(p.lastName) LIKE :search
                                OR a.phone LIKE :search
                              )
                        GROUP BY o.paymentStatus
                        """)
        List<Object[]> countByPaymentStatusWithFilters(
                        @Param("status") OrderStatus status,
                        @Param("paymentStatus") PaymentStatus paymentStatus,
                        @Param("paymentMethod") PaymentMethod paymentMethod,
                        @Param("dateFrom") Instant dateFrom,
                        @Param("dateTo") Instant dateTo,
                        @Param("search") String searchPattern);

        @Query("""
                        SELECT o.paymentMethod, COUNT(o) FROM Order o
                        JOIN o.user u
                        LEFT JOIN o.address a
                        LEFT JOIN u.profile p
                        WHERE (:status IS NULL OR o.status = :status)
                          AND (:paymentStatus IS NULL OR o.paymentStatus = :paymentStatus)
                          AND (:paymentMethod IS NULL OR o.paymentMethod = :paymentMethod)
                          AND o.createdAt >= :dateFrom
                          AND o.createdAt < :dateTo
                          AND (
                                :search IS NULL
                                OR LOWER(o.orderNumber) LIKE :search
                                OR CAST(o.id AS string) LIKE :search
                                OR LOWER(u.email) LIKE :search
                                OR LOWER(p.firstName) LIKE :search
                                OR LOWER(p.lastName) LIKE :search
                                OR a.phone LIKE :search
                              )
                        GROUP BY o.paymentMethod
                        """)
        List<Object[]> countByPaymentMethodWithFilters(
                        @Param("status") OrderStatus status,
                        @Param("paymentStatus") PaymentStatus paymentStatus,
                        @Param("paymentMethod") PaymentMethod paymentMethod,
                        @Param("dateFrom") Instant dateFrom,
                        @Param("dateTo") Instant dateTo,
                        @Param("search") String searchPattern);
}