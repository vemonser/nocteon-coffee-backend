package com.nocteon.nocteon_api.dashboard.service;

import com.nocteon.nocteon_api.auth.enums.Role;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.contact.repository.ContactMessageRepository;
import com.nocteon.nocteon_api.dashboard.dto.DashboardOverviewResponse;
import com.nocteon.nocteon_api.dashboard.dto.DashboardOverviewWithPreviousResponse;
import com.nocteon.nocteon_api.dashboard.dto.OrdersByStatusDto;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.order.repository.OrderItemRepository;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.review.repository.ReviewRepository;
import com.nocteon.nocteon_api.contact.repository.ContactMessageRepository;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final DashboardStatsBuilder statsBuilder;
    private final ReviewRepository reviewRepository;
    private final ContactMessageRepository contactMessageRepository;

    private static final Duration ONLINE_THRESHOLD = Duration.ofMinutes(5);

    public DashboardOverviewWithPreviousResponse getOverviewWithPrevious(int days) {
        Instant now = Instant.now();
        Instant currentPeriodStart = now.minus(days, ChronoUnit.DAYS);
        Instant previousPeriodStart = now.minus(days * 2L, ChronoUnit.DAYS);

        DashboardOverviewResponse current = buildOverview(currentPeriodStart, Instant.now(), days);
        DashboardOverviewResponse previous = buildOverview(previousPeriodStart, now.minus(days, ChronoUnit.DAYS), days);

        BigDecimal revenueGrowth = calculateGrowthPercentage(
                current.getTotalRevenue(), previous.getTotalRevenue());
        BigDecimal ordersGrowth = calculateGrowthPercentage(
                BigDecimal.valueOf(current.getTotalOrders()), BigDecimal.valueOf(previous.getTotalOrders()));
        BigDecimal coffeeGrowth = calculateGrowthPercentage(
                current.getCoffeeSoldKg(), previous.getCoffeeSoldKg());
        BigDecimal aovGrowth = calculateGrowthPercentage(
                current.getAverageOrderValue(), previous.getAverageOrderValue());

        return DashboardOverviewWithPreviousResponse.builder()
                        .current(current)
                        .previous(previous)
                        .revenueGrowthPercentage(revenueGrowth)
                        .ordersGrowthPercentage(ordersGrowth)
                        .coffeeSoldGrowthPercentage(coffeeGrowth)
                        .aovGrowthPercentage(aovGrowth)
                        .build();
    }

    private DashboardOverviewResponse buildOverview(Instant since, Instant until, int days) {
        String language = LocaleContextHolder.getLocale().getLanguage();

        BigDecimal coffeeSoldGrams = orderItemRepository.getTotalCoffeeSoldGrams(
                        PaymentStatus.PAID, since, ProductType.COFFEE);
        BigDecimal coffeeSoldKg = coffeeSoldGrams.divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);

        List<Object[]> revenueStatsResult = orderRepository.getRevenueStats(PaymentStatus.PAID, since);
        Object[] revenueStats = revenueStatsResult.isEmpty()
                        ? new Object[]{BigDecimal.ZERO, 0L}
                        : revenueStatsResult.get(0);

        BigDecimal totalRevenue = (BigDecimal) revenueStats[0];
        long totalOrders = (Long) revenueStats[1];

        BigDecimal averageOrderValue = totalOrders > 0
                        ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

        return DashboardOverviewResponse.builder()
                        .totalRevenue(totalRevenue)
                        .totalOrders(totalOrders)
                        .averageOrderValue(averageOrderValue)
                        .coffeeSoldKg(coffeeSoldKg)
                        .userGrowth(statsBuilder.buildUserGrowth(days))
                        .onlineUsersCount(userRepository.countByRoleAndLastActiveAtAfter(
                                        Role.CUSTOMER, Instant.now().minus(ONLINE_THRESHOLD)))
                        .deviceBreakdown(statsBuilder.buildDeviceBreakdown(since))
                        .revenueByDay(statsBuilder.buildFilledRevenueByDay(
                                        orderRepository.getRevenueByDay(PaymentStatus.PAID, since), days))
                        .ordersByStatus(orderRepository.getOrdersByStatus(PaymentStatus.PAID, since)
                                        .stream()
                                        .map(row -> OrdersByStatusDto.builder()
                                                                .status((OrderStatus) row[0])
                                                                .count((Long) row[1])
                                                                .build())
                                        .toList())
                        .revenueByCategory(statsBuilder.buildRevenueByCategory(since, totalRevenue, language))
                        .topSellingProducts(statsBuilder.buildTopSellingProducts(since, language))
                        .revenueByOrigin(statsBuilder.buildRevenueByOrigin(since, totalRevenue, language))
                        .revenueByRoastLevel(statsBuilder.buildRevenueByRoastLevel(since, totalRevenue,
                                        language))
                        .refundRate(statsBuilder.buildRefundRate(since))
                        .lowStockProducts(statsBuilder.buildLowStockProducts(language))
                        .ordersByPaymentMethod(statsBuilder.buildOrdersByPaymentMethod(since))
                        .repeatPurchaseRate(statsBuilder.buildRepeatPurchaseRate(since))
                        .build();
    }

    private BigDecimal calculateGrowthPercentage(BigDecimal current, BigDecimal previous) {
            if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
                    return current == null || current.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(100);
            }
            return current.subtract(previous)
                            .divide(previous, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP);
    }

    private long daysBetween(Instant start, Instant end) {
            return java.time.Duration.between(start, end).toDays();
    }

    public DashboardOverviewResponse getOverview(int days) {
            Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
            String language = LocaleContextHolder.getLocale().getLanguage();

            BigDecimal coffeeSoldGrams = orderItemRepository.getTotalCoffeeSoldGrams(
                            PaymentStatus.PAID, since, ProductType.COFFEE);
            BigDecimal coffeeSoldKg = coffeeSoldGrams.divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);

            List<Object[]> revenueStatsResult = orderRepository.getRevenueStats(PaymentStatus.PAID, since);
            Object[] revenueStats = revenueStatsResult.isEmpty()
                            ? new Object[]{BigDecimal.ZERO, 0L}
                            : revenueStatsResult.get(0);

            BigDecimal totalRevenue = (BigDecimal) revenueStats[0];
            long totalOrders = (Long) revenueStats[1];

            BigDecimal averageOrderValue = totalOrders > 0
                            ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

            return DashboardOverviewResponse.builder()
                            .totalRevenue(totalRevenue)
                            .totalOrders(totalOrders)
                            .averageOrderValue(averageOrderValue)
                            .coffeeSoldKg(coffeeSoldKg)
                            .userGrowth(statsBuilder.buildUserGrowth(days))
                            .onlineUsersCount(userRepository.countByRoleAndLastActiveAtAfter(
                                            Role.CUSTOMER, Instant.now().minus(ONLINE_THRESHOLD)))
                        .deviceBreakdown(statsBuilder.buildDeviceBreakdown(since))
                        .revenueByDay(statsBuilder.buildFilledRevenueByDay(
                                        orderRepository.getRevenueByDay(PaymentStatus.PAID, since), days))
                        .ordersByStatus(orderRepository.getOrdersByStatus(PaymentStatus.PAID, since)
                                        .stream()
                                        .map(row -> OrdersByStatusDto.builder()
                                                                .status((OrderStatus) row[0])
                                                                .count((Long) row[1])
                                                                .build())
                                        .toList())
                        .revenueByCategory(statsBuilder.buildRevenueByCategory(since, totalRevenue, language))
                        .topSellingProducts(statsBuilder.buildTopSellingProducts(since, language))
                        .revenueByOrigin(statsBuilder.buildRevenueByOrigin(since, totalRevenue, language))
                        .revenueByRoastLevel(statsBuilder.buildRevenueByRoastLevel(since, totalRevenue,
                                        language))
                        .refundRate(statsBuilder.buildRefundRate(since))
                        .lowStockProducts(statsBuilder.buildLowStockProducts(language))
                        .ordersByPaymentMethod(statsBuilder.buildOrdersByPaymentMethod(since))
                        .repeatPurchaseRate(statsBuilder.buildRepeatPurchaseRate(since))
                        .build();
    }

    public long getPendingReviewsCount() {
            return reviewRepository.countByIsApprovedFalse();
    }

    public long getUnreadContactMessagesCount() {
            return contactMessageRepository.countByIsReadFalse();
    }
}