package com.nocteon.nocteon_api.dashboard.service;

import com.nocteon.nocteon_api.auth.enums.Role;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.category.entity.CategoryTranslation;
import com.nocteon.nocteon_api.category.repository.CategoryRepository;
import com.nocteon.nocteon_api.dashboard.dto.*;
import com.nocteon.nocteon_api.origin.entity.Origin;
import com.nocteon.nocteon_api.origin.repository.OriginRepository;
import com.nocteon.nocteon_api.order.repository.OrderItemRepository;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.payment.enums.PaymentMethod;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductMedia;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevel;
import com.nocteon.nocteon_api.roastLevel.repository.RoastLevelRepository;
import com.nocteon.nocteon_api.userActivity.dto.DeviceBreakdownDto;
import com.nocteon.nocteon_api.userActivity.enums.DeviceType;
import com.nocteon.nocteon_api.userActivity.repository.LoginActivityRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DashboardStatsBuilder {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final LoginActivityRepository loginActivityRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OriginRepository originRepository;
    private final RoastLevelRepository roastLevelRepository;

    private static final int TOP_PRODUCTS_LIMIT = 5;

    List<TopSellingProductDto> buildTopSellingProducts(Instant since, String language) {
            List<Object[]> rows = orderItemRepository.getTopSellingProductIds(
                            PaymentStatus.PAID, since, PageRequest.of(0, TOP_PRODUCTS_LIMIT));

            if (rows.isEmpty()) {
                    return List.of();
            }

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

    List<RevenueByCategoryDto> buildRevenueByCategory(Instant since, BigDecimal totalRevenue, String language) {
            List<Object[]> rows = orderItemRepository.getRevenueByCategory(PaymentStatus.PAID, since);

            List<String> slugs = rows.stream().map(row -> (String) row[0]).toList();
            // Translations are read below and open-in-view is off, so they must be
            // fetched with the rows (findBySlugIn leaves them lazy).
            List<Category> categories = categoryRepository.findBySlugInWithTranslations(slugs);
            Map<String, Category> categoryBySlug = categories.stream()
                            .collect(Collectors.toMap(Category::getSlug, c -> c, (a, b) -> a));

            return rows.stream()
                            .map(row -> {
                                    String slug = (String) row[0];
                                    BigDecimal revenue = (BigDecimal) row[1];

                                    Category category = categoryBySlug.get(slug);
                                    String categoryName = slug;
                                    if (category != null) {
                                            categoryName = category.getTranslations().stream()
                                                            .filter(t -> t.getLanguage().equals(language))
                                                            .findFirst()
                                                            .map(t -> t.getName())
                                                            .orElseGet(() -> category.getTranslations().stream()
                                                                            .findFirst()
                                                                            .map(t -> t.getName())
                                                                            .orElse(slug));
                                    }

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

    List<RevenueByOriginDto> buildRevenueByOrigin(Instant since, BigDecimal totalRevenue, String language) {
            List<Object[]> rows = orderItemRepository.getRevenueByOrigin(PaymentStatus.PAID, since);

            List<String> slugs = rows.stream().map(row -> (String) row[0]).toList();
            List<Origin> origins = originRepository.findBySlugInWithTranslations(slugs);
            Map<String, Origin> originBySlug = origins.stream()
                            .collect(Collectors.toMap(Origin::getSlug, o -> o));

            return rows.stream()
                            .map(row -> {
                                    String slug = (String) row[0];
                                    BigDecimal revenue = (BigDecimal) row[1];

                                    Origin origin = originBySlug.get(slug);
                                    String originName = slug;
                                    if (origin != null) {
                                            originName = origin.getTranslations().stream()
                                                            .filter(t -> t.getLanguage().equals(language))
                                                            .findFirst()
                                                            .map(t -> t.getName())
                                                            .orElseGet(() -> origin.getTranslations().stream()
                                                                            .findFirst()
                                                                            .map(t -> t.getName())
                                                                            .orElse(slug));
                                    }

                                    BigDecimal percentage = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                                                    ? revenue.divide(totalRevenue, 4, RoundingMode.HALF_UP)
                                                                    .multiply(BigDecimal.valueOf(100))
                                                    : BigDecimal.ZERO;

                                    return RevenueByOriginDto.builder()
                                                    .originName(originName)
                                                    .revenue(revenue)
                                                    .percentage(percentage.setScale(1, RoundingMode.HALF_UP))
                                                    .build();
                            })
                            .toList();
    }

    List<RevenueByRoastLevelDto> buildRevenueByRoastLevel(Instant since, BigDecimal totalRevenue,
                    String language) {
            List<Object[]> rows = orderItemRepository.getRevenueByRoastLevel(PaymentStatus.PAID, since);

            List<String> slugs = rows.stream().map(row -> (String) row[0]).toList();
            List<RoastLevel> roastLevels = roastLevelRepository.findBySlugInWithTranslations(slugs);
            Map<String, RoastLevel> roastLevelBySlug = roastLevels.stream()
                            .collect(Collectors.toMap(RoastLevel::getSlug, r -> r));

            return rows.stream()
                            .map(row -> {
                                    String slug = (String) row[0];
                                    BigDecimal revenue = (BigDecimal) row[1];

                                    RoastLevel rl = roastLevelBySlug.get(slug);
                                    String roastLevelName = slug;
                                    if (rl != null) {
                                            roastLevelName = rl.getTranslations().stream()
                                                            .filter(t -> t.getLanguage().equals(language))
                                                            .findFirst()
                                                            .map(t -> t.getName())
                                                            .orElseGet(() -> rl.getTranslations().stream()
                                                                            .findFirst()
                                                                            .map(t -> t.getName())
                                                                            .orElse(slug));
                                    }

                                    BigDecimal percentage = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                                                    ? revenue.divide(totalRevenue, 4, RoundingMode.HALF_UP)
                                                                    .multiply(BigDecimal.valueOf(100))
                                                    : BigDecimal.ZERO;

                                    return RevenueByRoastLevelDto.builder()
                                                    .roastLevelName(roastLevelName)
                                                    .revenue(revenue)
                                                    .percentage(percentage.setScale(1, RoundingMode.HALF_UP))
                                                    .build();
                            })
                            .toList();
    }

    RefundRateDto buildRefundRate(Instant since) {
            long totalPaid = orderRepository.countByPaymentStatusAndCreatedAtAfter(PaymentStatus.PAID, since);
            long totalRefunded = orderRepository.countByPaymentStatusAndCreatedAtAfter(PaymentStatus.REFUNDED,
                            since);

            BigDecimal refundRate = totalPaid > 0
                            ? BigDecimal.valueOf(totalRefunded)
                                            .divide(BigDecimal.valueOf(totalPaid), 4, RoundingMode.HALF_UP)
                                            .multiply(BigDecimal.valueOf(100))
                                            .setScale(1, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

            return RefundRateDto.builder()
                            .totalPaid(totalPaid)
                            .totalRefunded(totalRefunded)
                            .refundRate(refundRate)
                            .build();
    }

    List<LowStockProductDto> buildLowStockProducts(String language) {
            List<Product> products = productRepository.findLowStockProducts();

            List<LowStockProductDto> result = new ArrayList<>();
            for (Product p : products) {
                    p.getVariants().stream()
                                    .filter(ProductVariant::isLowStock)
                                    .findFirst()
                                    .ifPresent(v -> {
                                            String name = p.getTranslations().stream()
                                                            .filter(t -> t.getLanguage().equals(language))
                                                            .findFirst()
                                                            .map(ProductTranslation::getName)
                                                            .orElseGet(() -> p.getTranslations().stream()
                                                                            .findFirst()
                                                                            .map(ProductTranslation::getName)
                                                                            .orElse(p.getSlug()));
                                            String imageUrl = p.getMedia().stream()
                                                            .filter(ProductMedia::isPrimary)
                                                            .findFirst()
                                                            .map(ProductMedia::getUrl)
                                                            .orElse(null);

                                            result.add(LowStockProductDto.builder()
                                                            .id(p.getId())
                                                            .slug(p.getSlug())
                                                            .name(name)
                                                            .imageUrl(imageUrl)
                                                            .stockQuantity(v.getAvailableStock())
                                                            .reservedQuantity(v.getReservedQuantity())
                                                            .reorderThreshold(v.getReorderThreshold())
                                                            .costPrice(v.getCostPrice())
                                                            .build());
                                    });
            }
            return result;
    }

    List<OrdersByPaymentMethodDto> buildOrdersByPaymentMethod(Instant since) {
            return orderRepository.getOrdersByPaymentMethod(PaymentStatus.PAID, since)
                            .stream()
                            .map(row -> OrdersByPaymentMethodDto.builder()
                                            .paymentMethod(((PaymentMethod) row[0]).name())
                                            .count((Long) row[1])
                                            .build())
                            .toList();
    }

    RepeatPurchaseRateDto buildRepeatPurchaseRate(Instant since) {
            long totalCustomers = userRepository.countCustomersWithOrdersSince(since);
            long repeatCustomers = userRepository.countRepeatCustomersSince(since);

            BigDecimal repeatRate = totalCustomers > 0
                            ? BigDecimal.valueOf(repeatCustomers)
                                            .divide(BigDecimal.valueOf(totalCustomers), 4, RoundingMode.HALF_UP)
                                            .multiply(BigDecimal.valueOf(100))
                                            .setScale(1, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

            return RepeatPurchaseRateDto.builder()
                            .totalCustomers(totalCustomers)
                            .repeatCustomers(repeatCustomers)
                            .repeatRate(repeatRate)
                            .build();
    }

    List<DeviceBreakdownDto> buildDeviceBreakdown(Instant since) {
            return loginActivityRepository.countByDeviceTypeSince(since).stream()
                            .map(row -> DeviceBreakdownDto.builder()
                                            .deviceType((DeviceType) row[0])
                                            .count((Long) row[1])
                                            .build())
                            .toList();
    }

    UserGrowthDto buildUserGrowth(int days) {
            Instant now = Instant.now();

            Instant currentPeriodStart = now.minus(days, ChronoUnit.DAYS);
            long currentPeriodCount = userRepository.countByRoleAndCreatedAtBetween(
                            "CUSTOMER", currentPeriodStart, now);

            Instant previousPeriodStart = now.minus(days * 2L, ChronoUnit.DAYS);
            long previousPeriodCount = userRepository.countByRoleAndCreatedAtBetween(
                            "CUSTOMER", previousPeriodStart, currentPeriodStart);

            long totalUsers = userRepository.countByRole("CUSTOMER");

            BigDecimal growthPercentage = calculateGrowthPercentage(currentPeriodCount, previousPeriodCount);

            return UserGrowthDto.builder()
                            .totalUsers(totalUsers)
                            .currentPeriodCount(currentPeriodCount)
                            .previousPeriodCount(previousPeriodCount)
                            .growthPercentage(growthPercentage)
                            .build();
    }

    BigDecimal calculateGrowthPercentage(long current, long previous) {
            if (previous == 0) {
                    return current == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(100);
            }
            return BigDecimal.valueOf(current - previous)
                            .divide(BigDecimal.valueOf(previous), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP);
    }

    List<RevenueByDayDto> buildFilledRevenueByDay(List<Object[]> rawResults, int days) {
            Map<LocalDate, BigDecimal> revenueMap = rawResults.stream()
                            .collect(Collectors.toMap(
                                            row -> {
                                    Object dateVal = row[0];
                                    if (dateVal instanceof java.sql.Date sqlDate) {
                                            return sqlDate.toLocalDate();
                                    }
                                    return (LocalDate) dateVal;
                            },
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
}
