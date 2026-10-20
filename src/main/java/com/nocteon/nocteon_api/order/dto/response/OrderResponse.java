package com.nocteon.nocteon_api.order.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.nocteon.nocteon_api.address.dto.response.AddressResponse;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.payment.enums.PaymentMethod;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class OrderResponse {
    private Long id;
    private String orderNumber;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private PaymentStatus paymentStatus;
    private PaymentMethod paymentMethod;
    private BigDecimal shippingCost;
    private BigDecimal discountAmount;
    private String notes;
    /**
     * Why the order was cancelled, when it was.
     *
     * <p>Null for every order that was not cancelled.
     */
    private String cancellationReason;
    /** The shipment: the carrier, the tracking number and the times. */
    private OrderShipmentResponse shipment;
    private List<OrderItemResponse> items;
    private Instant createdAt;
    private AddressResponse address;
    private CustomerInfo customer;

    @Getter
    @Setter
    @Builder
    public static class CustomerInfo {
        private Long id;
        private String name;
        private String email;
        private String avatar;
    }
}
