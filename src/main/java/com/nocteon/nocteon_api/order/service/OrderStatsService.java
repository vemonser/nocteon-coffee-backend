package com.nocteon.nocteon_api.order.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.order.dto.request.OrderFilterRequest;
import com.nocteon.nocteon_api.order.dto.response.OrderStatsResponse;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.order.service.OrderQueryService;
import com.nocteon.nocteon_api.common.repository.SearchPatterns;

import lombok.RequiredArgsConstructor;

/**
 * The counts behind the orders list.
 *
 * <p>The counts are of the FILTERED set — the same set the table shows — so
 * a card can never contradict the rows below it. That is the whole point of
 * the endpoint: before it, the cards read the DASHBOARD's 30-day window while
 * the table read the list, and the two numbers were both right and
 * irreconcilable.
 */
@Service
@RequiredArgsConstructor
public class OrderStatsService {

        private final OrderRepository orderRepository;

        /** The counts of the filtered set. */
        @Transactional(readOnly = true)
        public OrderStatsResponse getStats(OrderFilterRequest filter) {
                String search = SearchPatterns.like(filter.getSearch());
                Instant dateFrom = OrderQueryService.startOfDay(filter.getDateFrom());
                Instant dateTo = OrderQueryService.startOfDayAfter(filter.getDateTo());

                List<Object[]> byStatus = orderRepository.countByStatusWithFilters(
                                filter.getStatus(),
                                filter.getPaymentStatus(),
                                filter.getPaymentMethod(),
                                dateFrom,
                                dateTo,
                                search);

                List<Object[]> byPaymentStatus = orderRepository.countByPaymentStatusWithFilters(
                                filter.getStatus(),
                                filter.getPaymentStatus(),
                                filter.getPaymentMethod(),
                                dateFrom,
                                dateTo,
                                search);

                List<Object[]> byPaymentMethod = orderRepository.countByPaymentMethodWithFilters(
                                filter.getStatus(),
                                filter.getPaymentStatus(),
                                filter.getPaymentMethod(),
                                dateFrom,
                                dateTo,
                                search);

                long total = byStatus.stream().mapToLong(row -> ((Number) row[1]).longValue()).sum();

                // Revenue: sum of PAID non-cancelled orders minus refunds
                List<Object[]> revenueAgg = orderRepository.getRevenueAggregate(
                                filter.getStatus(),
                                filter.getPaymentStatus(),
                                filter.getPaymentMethod(),
                                dateFrom,
                                dateTo,
                                search);
                BigDecimal gross = revenueAgg.isEmpty() ? BigDecimal.ZERO : (BigDecimal) revenueAgg.get(0)[0];
                long revenueCount = revenueAgg.isEmpty() ? 0L : ((Number) revenueAgg.get(0)[1]).longValue();

                BigDecimal refunded = orderRepository.getRefundedAggregate(
                                filter.getStatus(),
                                filter.getPaymentStatus(),
                                filter.getPaymentMethod(),
                                dateFrom,
                                dateTo,
                                search);
                if (refunded == null) refunded = BigDecimal.ZERO;

                BigDecimal revenue = gross.subtract(refunded);

                return OrderStatsResponse.builder()
                                .total(total)
                                .byStatus(byStatus.stream()
                                                .map(row -> OrderStatsResponse.StatusCount.builder()
                                                                .status(String.valueOf(row[0]))
                                                                .count(((Number) row[1]).longValue())
                                                                .build())
                                                .toList())
                                .byPaymentStatus(byPaymentStatus.stream()
                                                .map(row -> OrderStatsResponse.PaymentStatusCount.builder()
                                                                .paymentStatus(String.valueOf(row[0]))
                                                                .count(((Number) row[1]).longValue())
                                                                .build())
                                                .toList())
                                .byPaymentMethod(byPaymentMethod.stream()
                                                .map(row -> OrderStatsResponse.PaymentMethodCount.builder()
                                                                .paymentMethod(String.valueOf(row[0]))
                                                                .count(((Number) row[1]).longValue())
                                                                .build())
                                                .toList())
                                .revenue(revenue)
                                .revenueOrderCount(revenueCount)
                                .build();
        }

        /**
         * The search term as a ready-made LIKE pattern, lower-cased — the
         * same shape the row query uses, so the counts and the rows can
         * never describe different sets.
         */

}
