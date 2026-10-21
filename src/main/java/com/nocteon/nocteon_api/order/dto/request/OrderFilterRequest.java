package com.nocteon.nocteon_api.order.dto.request;

import java.time.LocalDate;

import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.payment.enums.PaymentMethod;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;

import lombok.Getter;
import lombok.Setter;

/**
 * The filters of the orders list.
 *
 * <p>Beyond the page and the sort, the list can narrow by the order status,
 * the payment status, the payment method, a created-at range and a free-text
 * search over the order number, the customer and the phone.
 *
 * <p>Every filter is OPTIONAL: an absent filter does not narrow anything, so
 * the counts behind the cards and the rows in the table are always the same
 * set.
 */
@Getter
@Setter
public class OrderFilterRequest extends BaseFilterRequest {

        private OrderStatus status;

        private Long userId;

        private PaymentStatus paymentStatus;

        private PaymentMethod paymentMethod;

        /** Inclusive first day of the created-at range. */
        private LocalDate dateFrom;

        /** Inclusive last day of the created-at range. */
        private LocalDate dateTo;

        /**
         * Free text: the order number, the customer's name, the e-mail or the
         * phone. An empty term matches everything.
         */
        private String search;
}
