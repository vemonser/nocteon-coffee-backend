package com.nocteon.nocteon_api.order.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.common.exception.notFound.OrderNotFoundException;
import com.nocteon.nocteon_api.order.dto.request.UpdateShipmentRequest;
import com.nocteon.nocteon_api.order.dto.response.OrderShipmentResponse;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

/**
 * The shipment of an order.
 *
 * <p>There is NO carrier API: the admin pastes what the carrier told them
 * and the page shows exactly that. The endpoint never invents a tracking
 * number and never guesses a carrier.
 */
@Service
@RequiredArgsConstructor
public class OrderShipmentService {

        private final OrderRepository orderRepository;

        /** Record (or change) the shipment of an order. */
        @Transactional
        public OrderShipmentResponse updateShipment(Long orderId, UpdateShipmentRequest request) {
                Order order = orderRepository.findById(orderId)
                                .orElseThrow(OrderNotFoundException::new);

                if (request.getCarrier() != null) {
                        order.setCarrier(blankToNull(request.getCarrier()));
                }
                if (request.getTrackingNumber() != null) {
                        order.setTrackingNumber(blankToNull(request.getTrackingNumber()));
                }
                if (request.getTrackingUrl() != null) {
                        order.setTrackingUrl(blankToNull(request.getTrackingUrl()));
                }
                orderRepository.save(order);

                return toResponse(order);
        }

        /** The shipment of an order, as it is. */
        @Transactional(readOnly = true)
        public OrderShipmentResponse getShipment(Long orderId) {
                Order order = orderRepository.findById(orderId)
                                .orElseThrow(OrderNotFoundException::new);
                return toResponse(order);
        }

        private static String blankToNull(String value) {
                return value == null || value.isBlank() ? null : value.trim();
        }

        static OrderShipmentResponse toResponse(Order order) {
                return OrderShipmentResponse.builder()
                                .carrier(order.getCarrier())
                                .trackingNumber(order.getTrackingNumber())
                                .trackingUrl(order.getTrackingUrl())
                                .shippedAt(order.getShippedAt())
                                .deliveredAt(order.getDeliveredAt())
                                .build();
        }
}
