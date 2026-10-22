package com.nocteon.nocteon_api.order.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * The shipment of an order.
 *
 * <p>There is NO carrier API: the admin pastes what the carrier told them,
 * and the page shows exactly that.
 */
@Getter
@Setter
public class UpdateShipmentRequest {

        @Size(max = 120, message = "{validation.carrier.size}")
        private String carrier;

        @Size(max = 120, message = "{validation.trackingNumber.size}")
        private String trackingNumber;

        @Size(max = 500, message = "{validation.trackingUrl.size}")
        private String trackingUrl;

        private Instant estimatedDeliveryAt;
}
