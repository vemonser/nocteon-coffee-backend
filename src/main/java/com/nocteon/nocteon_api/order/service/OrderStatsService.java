package com.nocteon.nocteon_api.order.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.order.dto.request.OrderFilterRequest;
import com.nocteon.nocteon_api.order.dto.response.OrderStatsResponse;
import com.nocteon.nocteon_api.order.repository.OrderRepository;

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
                String search = normalise(filter.getSearch());

                List<Object[]> byStatus = orderRepository.countByStatusWithFilters(
                                filter.getStatus(),
                                filter.getPaymentStatus(),
                                filter.getPaymentMethod(),
                                OrderQueryService.startOfDay(filter.getDateFrom()),
                                OrderQueryService.startOfDayAfter(filter.getDateTo()),
                                search);

                List<Object[]> byPaymentStatus = orderRepository.countByPaymentStatusWithFilters(
                                filter.getStatus(),
                                filter.getPaymentStatus(),
                                filter.getPaymentMethod(),
                                OrderQueryService.startOfDay(filter.getDateFrom()),
                                OrderQueryService.startOfDayAfter(filter.getDateTo()),
                                search);

                List<Object[]> byPaymentMethod = orderRepository.countByPaymentMethodWithFilters(
                                filter.getStatus(),
                                filter.getPaymentStatus(),
                                filter.getPaymentMethod(),
                                OrderQueryService.startOfDay(filter.getDateFrom()),
                                OrderQueryService.startOfDayAfter(filter.getDateTo()),
                                search);

                long total = byStatus.stream().mapToLong(row -> ((Number) row[1]).longValue()).sum();

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
                                .build();
        }

        /**
         * The search term as a ready-made LIKE pattern, lower-cased — the
         * same shape the row query uses, so the counts and the rows can
         * never describe different sets.
         */
        private static String normalise(String search) {
                if (search == null || search.isBlank()) {
                        return null;
                }
                return "%" + search.trim().toLowerCase() + "%";
        }
}
