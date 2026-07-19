package com.nocteon.nocteon_api.dashboard.dto;

import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.util.List;

import com.nocteon.nocteon_api.userActivity.dto.DeviceBreakdownDto;

@Getter
@Builder
public class DashboardOverviewResponse {
    private BigDecimal totalRevenue;
    private long totalOrders;
    private BigDecimal averageOrderValue;
    private BigDecimal coffeeSoldKg;
    private UserGrowthDto userGrowth;
    private long onlineUsersCount;
    private List<DeviceBreakdownDto> deviceBreakdown;
    private List<RevenueByDayDto> revenueByDay;
    private List<OrdersByStatusDto> ordersByStatus;
    private List<RevenueByCategoryDto> revenueByCategory; 
    private List<TopSellingProductDto> topSellingProducts; 
    private List<RevenueByOriginDto> revenueByOrigin;
    private List<RevenueByRoastLevelDto> revenueByRoastLevel;
    private RefundRateDto refundRate;
    private List<LowStockProductDto> lowStockProducts;
    private List<OrdersByPaymentMethodDto> ordersByPaymentMethod;
    private RepeatPurchaseRateDto repeatPurchaseRate;

}