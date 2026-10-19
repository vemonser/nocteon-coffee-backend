package com.nocteon.nocteon_api.dashboard.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
public class DashboardOverviewWithPreviousResponse {
    private DashboardOverviewResponse current;
    private DashboardOverviewResponse previous;
    private BigDecimal revenueGrowthPercentage;
    private BigDecimal ordersGrowthPercentage;
    private BigDecimal coffeeSoldGrowthPercentage;
    private BigDecimal aovGrowthPercentage;
    public DashboardOverviewResponse getCurrent() {
        return current;
    }

    public void setCurrent(DashboardOverviewResponse current) {
        this.current = current;
    }

    public DashboardOverviewResponse getPrevious() {
        return previous;
    }

    public void setPrevious(DashboardOverviewResponse previous) {
        this.previous = previous;
    }

    public BigDecimal getRevenueGrowthPercentage() {
        return revenueGrowthPercentage;
    }

    public void setRevenueGrowthPercentage(BigDecimal revenueGrowthPercentage) {
        this.revenueGrowthPercentage = revenueGrowthPercentage;
    }

    public BigDecimal getOrdersGrowthPercentage() {
        return ordersGrowthPercentage;
    }

    public void setOrdersGrowthPercentage(BigDecimal ordersGrowthPercentage) {
        this.ordersGrowthPercentage = ordersGrowthPercentage;
    }

    public BigDecimal getCoffeeSoldGrowthPercentage() {
        return coffeeSoldGrowthPercentage;
    }

    public void setCoffeeSoldGrowthPercentage(BigDecimal coffeeSoldGrowthPercentage) {
        this.coffeeSoldGrowthPercentage = coffeeSoldGrowthPercentage;
    }

    public BigDecimal getAovGrowthPercentage() {
        return aovGrowthPercentage;
    }

    public void setAovGrowthPercentage(BigDecimal aovGrowthPercentage) {
        this.aovGrowthPercentage = aovGrowthPercentage;
    }


    // Manual builder for Lombok compatibility
    public static class DashboardOverviewWithPreviousResponseBuilder {
        private DashboardOverviewResponse current;
        private DashboardOverviewResponse previous;
        private BigDecimal revenueGrowthPercentage;
        private BigDecimal ordersGrowthPercentage;
        private BigDecimal coffeeSoldGrowthPercentage;
        private BigDecimal aovGrowthPercentage;

        public DashboardOverviewWithPreviousResponseBuilder current(DashboardOverviewResponse current) {
            this.current = current;
            return this;
        }
        public DashboardOverviewWithPreviousResponseBuilder previous(DashboardOverviewResponse previous) {
            this.previous = previous;
            return this;
        }
        public DashboardOverviewWithPreviousResponseBuilder revenueGrowthPercentage(BigDecimal revenueGrowthPercentage) {
            this.revenueGrowthPercentage = revenueGrowthPercentage;
            return this;
        }
        public DashboardOverviewWithPreviousResponseBuilder ordersGrowthPercentage(BigDecimal ordersGrowthPercentage) {
            this.ordersGrowthPercentage = ordersGrowthPercentage;
            return this;
        }
        public DashboardOverviewWithPreviousResponseBuilder coffeeSoldGrowthPercentage(BigDecimal coffeeSoldGrowthPercentage) {
            this.coffeeSoldGrowthPercentage = coffeeSoldGrowthPercentage;
            return this;
        }
        public DashboardOverviewWithPreviousResponseBuilder aovGrowthPercentage(BigDecimal aovGrowthPercentage) {
            this.aovGrowthPercentage = aovGrowthPercentage;
            return this;
        }

        public DashboardOverviewWithPreviousResponse build() {
            DashboardOverviewWithPreviousResponse dto = new DashboardOverviewWithPreviousResponse();
            dto.setCurrent(current);
            dto.setPrevious(previous);
            dto.setRevenueGrowthPercentage(revenueGrowthPercentage);
            dto.setOrdersGrowthPercentage(ordersGrowthPercentage);
            dto.setCoffeeSoldGrowthPercentage(coffeeSoldGrowthPercentage);
            dto.setAovGrowthPercentage(aovGrowthPercentage);
            return dto;
        }
    }
}