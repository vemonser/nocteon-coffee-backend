package com.nocteon.nocteon_api.dashboard.service;

import com.nocteon.nocteon_api.auth.enums.Role;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.dashboard.dto.DashboardOverviewResponse;
import com.nocteon.nocteon_api.dashboard.dto.OrdersByStatusDto;
import com.nocteon.nocteon_api.dashboard.dto.RevenueByCategoryDto;
import com.nocteon.nocteon_api.dashboard.dto.RevenueByDayDto;
import com.nocteon.nocteon_api.dashboard.dto.TopSellingProductDto;
import com.nocteon.nocteon_api.dashboard.dto.UserGrowthDto;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.order.repository.OrderItemRepository;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductMedia;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.userActivity.dto.DeviceBreakdownDto;
import com.nocteon.nocteon_api.userActivity.enums.DeviceType;
import com.nocteon.nocteon_api.userActivity.repository.LoginActivityRepository;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {
        private final OrderRepository orderRepository;
        private final OrderItemRepository orderItemRepository;
        private final UserRepository userRepository;
        private final LoginActivityRepository loginActivityRepository;
        private final ProductRepository productRepository;

        private static final Duration ONLINE_THRESHOLD = Duration.ofMinutes(5);
        private static final int TOP_PRODUCTS_LIMIT = 5;

        private List<TopSellingProductDto> buildTopSellingProducts(Instant since, String language) {
                List<Object[]> rows = orderItemRepository.getTopSellingProductIds(
                                PaymentStatus.PAID, since, PageRequest.of(0, TOP_PRODUCTS_LIMIT));

                if (rows.isEmpty()) {
                        return List.of();
                }

                // خريطة: productId -> quantitySold (بترتيب الأكتر مبيعًا محفوظ من الـ query
                // الأول)
                Map<Long, Long> quantityByProductId = new LinkedHashMap<>();
                for (Object[] row : rows) {
                        quantityByProductId.put((Long) row[0], (Long) row[2]);
                }

                List<Long> productIds = new ArrayList<>(quantityByProductId.keySet());
                List<Product> products = productRepository.findAllByIdInWithDetails(productIds);

                Map<Long, Product> productById = products.stream()
                                .collect(Collectors.toMap(Product::getId, p -> p));

                return productIds.stream()
                                .filter(productById::containsKey)
                                .map(id -> {
                                        Product product = productById.get(id);

                                        ProductTranslation translation = product.getTranslations().stream()
                                                        .filter(t -> t.getLanguage().equals(language))
                                                        .findFirst()
                                                        .orElseGet(() -> product.getTranslations().stream().findFirst()
                                                                        .orElse(null));
                                        String imageUrl = product.getMedia().stream()
                                                        .filter(ProductMedia::isPrimary)
                                                        .findFirst()
                                                        .map(ProductMedia::getUrl)
                                                        .orElse(null);

                                        BigDecimal minPrice = product.getVariants().stream()
                                                        .map(ProductVariant::getPrice)
                                                        .min(BigDecimal::compareTo)
                                                        .orElse(BigDecimal.ZERO);

                                        return TopSellingProductDto.builder()
                                                        .id(product.getId())
                                                        .slug(product.getSlug())
                                                        .name(translation != null ? translation.getName() : null)
                                                        .imageUrl(imageUrl)
                                                        .price(minPrice)
                                                        .quantitySold(quantityByProductId.get(id))
                                                        .build();
                                }).toList();
        }

        private List<RevenueByCategoryDto> buildRevenueByCategory(Instant since, BigDecimal totalRevenue) {
                List<Object[]> rows = orderItemRepository.getRevenueByCategory(PaymentStatus.PAID, since);

                return rows.stream()
                                .map(row -> {
                                        String categoryName = (String) row[0];
                                        BigDecimal revenue = (BigDecimal) row[1];
                                        BigDecimal percentage = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                                                        ? revenue.divide(totalRevenue, 4, RoundingMode.HALF_UP)
                                                                        .multiply(BigDecimal.valueOf(100))
                                                        : BigDecimal.ZERO;

                                        return RevenueByCategoryDto.builder()
                                                        .categoryName(categoryName)
                                                        .revenue(revenue)
                                                        .percentage(percentage.setScale(1, RoundingMode.HALF_UP))
                                                        .build();
                                })
                                .toList();
        }

        private List<DeviceBreakdownDto> buildDeviceBreakdown(Instant since) {
                return loginActivityRepository.countByDeviceTypeSince(since).stream()
                                .map(row -> DeviceBreakdownDto.builder()
                                                .deviceType((DeviceType) row[0])
                                                .count((Long) row[1])
                                                .build())
                                .toList();
        }

        private UserGrowthDto buildUserGrowth(int days) {
                Instant now = Instant.now();

                Instant currentPeriodStart = now.minus(days, ChronoUnit.DAYS);
                long currentPeriodCount = userRepository.countByRoleAndCreatedAtBetween(
                                Role.CUSTOMER, currentPeriodStart, now);

                Instant previousPeriodStart = now.minus(days * 2L, ChronoUnit.DAYS);
                long previousPeriodCount = userRepository.countByRoleAndCreatedAtBetween(
                                Role.CUSTOMER, previousPeriodStart, currentPeriodStart);

                long totalUsers = userRepository.countByRole(Role.CUSTOMER);

                BigDecimal growthPercentage = calculateGrowthPercentage(currentPeriodCount, previousPeriodCount);

                return UserGrowthDto.builder()
                                .totalUsers(totalUsers)
                                .currentPeriodCount(currentPeriodCount)
                                .previousPeriodCount(previousPeriodCount)
                                .growthPercentage(growthPercentage)
                                .build();
        }

        private BigDecimal calculateGrowthPercentage(long current, long previous) {
                if (previous == 0) {
                        return current == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(100);
                }
                return BigDecimal.valueOf(current - previous)
                                .divide(BigDecimal.valueOf(previous), 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP);
        }

        private List<RevenueByDayDto> buildFilledRevenueByDay(List<Object[]> rawResults, int days) {
                Map<LocalDate, BigDecimal> revenueMap = rawResults.stream()
                                .collect(Collectors.toMap(
                                                row -> ((java.sql.Date) row[0]).toLocalDate(),
                                                row -> (BigDecimal) row[1]));

                LocalDate today = LocalDate.now();
                List<RevenueByDayDto> filled = new ArrayList<>();

                for (int i = days - 1; i >= 0; i--) {
                        LocalDate date = today.minusDays(i);
                        BigDecimal revenue = revenueMap.getOrDefault(date, BigDecimal.ZERO);
                        filled.add(RevenueByDayDto.builder()
                                        .date(date)
                                        .revenue(revenue)
                                        .build());
                }
                return filled;
        }

        public DashboardOverviewResponse getOverview(int days) {
                Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
                String language = LocaleContextHolder.getLocale().getLanguage();

                BigDecimal coffeeSoldGrams = orderItemRepository.getTotalCoffeeSoldGrams(
                                PaymentStatus.PAID, since, ProductType.COFFEE);
                BigDecimal coffeeSoldKg = coffeeSoldGrams.divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);

                List<Object[]> revenueStatsResult = orderRepository.getRevenueStats(PaymentStatus.PAID, since);
                Object[] revenueStats = revenueStatsResult.get(0);

                BigDecimal totalRevenue = (BigDecimal) revenueStats[0];
                long totalOrders = (Long) revenueStats[1];

                BigDecimal averageOrderValue = totalOrders > 0
                                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO;

                List<RevenueByDayDto> revenueByDay = buildFilledRevenueByDay(
                                orderRepository.getRevenueByDay(PaymentStatus.PAID, since), days);

                List<OrdersByStatusDto> ordersByStatus = orderRepository.getOrdersByStatus(since)
                                .stream()
                                .map(row -> OrdersByStatusDto.builder()
                                                .status((OrderStatus) row[0])
                                                .count((Long) row[1])
                                                .build())
                                .toList();

                UserGrowthDto userGrowth = buildUserGrowth(days);
                long onlineUsersCount = userRepository.countByLastActiveAtAfter(
                                Instant.now().minus(ONLINE_THRESHOLD));
                List<DeviceBreakdownDto> deviceBreakdown = buildDeviceBreakdown(since);

                List<RevenueByCategoryDto> revenueByCategory = buildRevenueByCategory(since, totalRevenue);
                List<TopSellingProductDto> topSellingProducts = buildTopSellingProducts(since, language);

                return DashboardOverviewResponse.builder()
                                .totalRevenue(totalRevenue)
                                .totalOrders(totalOrders)
                                .averageOrderValue(averageOrderValue)
                                .coffeeSoldKg(coffeeSoldKg)
                                .userGrowth(userGrowth)
                                .onlineUsersCount(onlineUsersCount)
                                .deviceBreakdown(deviceBreakdown)
                                .revenueByDay(revenueByDay)
                                .ordersByStatus(ordersByStatus)
                                .revenueByCategory(revenueByCategory)
                                .topSellingProducts(topSellingProducts)
                                .build();
        }
}