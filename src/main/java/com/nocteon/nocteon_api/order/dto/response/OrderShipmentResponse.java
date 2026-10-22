package com.nocteon.nocteon_api.order.dto.response;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * The shipment of an order.
 *
 * <p>Every field is nullable: an order that has not shipped has no carrier
 * and no tracking number, and the page says so rather than showing an empty
 * box.
 */
@Getter
@Setter
@Builder
public class OrderShipmentResponse {

        private String carrier;

        private String trackingNumber;

        private String trackingUrl;

        private Instant shippedAt;

        private Instant deliveredAt;

        private Instant estimatedDeliveryAt;
}
