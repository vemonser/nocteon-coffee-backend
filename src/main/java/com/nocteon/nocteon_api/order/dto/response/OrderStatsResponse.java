package com.nocteon.nocteon_api.order.dto.response;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * The counts behind the orders list.
 *
 * <p>The counts are of the FILTERED set — the same set the table shows — so
 * a card can never contradict the rows below it.
 */
@Getter
@Setter
@Builder
public class OrderStatsResponse {

        /** How many orders the filter selects. */
        private long total;

        private List<StatusCount> byStatus;

        private List<PaymentStatusCount> byPaymentStatus;

        private List<PaymentMethodCount> byPaymentMethod;

        /** The money the set brought in: the paid orders, less the refunds. */
        private BigDecimal revenue;

        /** How many orders the revenue was summed over. */
        private long revenueOrderCount;

        @Getter
        @Setter
        @Builder
        public static class StatusCount {
                private String status;
                private long count;
        }

        @Getter
        @Setter
        @Builder
        public static class PaymentStatusCount {
                private String paymentStatus;
                private long count;
        }

        @Getter
        @Setter
        @Builder
        public static class PaymentMethodCount {
                private String paymentMethod;
                private long count;
        }
}
