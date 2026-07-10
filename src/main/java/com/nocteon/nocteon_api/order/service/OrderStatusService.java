package com.nocteon.nocteon_api.order.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.common.exception.notFound.OrderNotFoundException;
import com.nocteon.nocteon_api.order.dto.response.OrderItemResponse;
import com.nocteon.nocteon_api.order.dto.response.OrderResponse;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.entity.OrderItem;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.order.event.OrderShippedEvent;
import com.nocteon.nocteon_api.order.exception.InvalidOrderStatusTransitionException;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.product.entity.ProductVariant;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderStatusService {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, Set.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED),
            OrderStatus.PROCESSING, Set.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
            OrderStatus.SHIPPED, Set.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, Set.of(),
            OrderStatus.CANCELLED, Set.of());

    @Transactional
    public OrderResponse updateStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(OrderNotFoundException::new);

        OrderStatus currentStatus = order.getStatus();

        if (!ALLOWED_TRANSITIONS.get(currentStatus).contains(newStatus)) {
            throw new InvalidOrderStatusTransitionException(currentStatus, newStatus);
        }

        order.setStatus(newStatus);
        orderRepository.save(order);

        log.info("Order {} status changed from {} to {}", orderId, currentStatus, newStatus);

        if (newStatus == OrderStatus.SHIPPED) {
            eventPublisher.publishEvent(new OrderShippedEvent(order.getId()));
        }

        return buildOrderResponse(order);
    }

    private OrderResponse buildOrderResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(this::buildOrderItemResponse)
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .paymentStatus(order.getPaymentStatus())
                .paymentMethod(order.getPaymentMethod())
                .shippingCost(order.getShippingCost())
                .discountAmount(order.getDiscountAmount())
                .notes(order.getNotes())
                .items(items)
                .createdAt(order.getCreatedAt())
                .build();
    }

    private OrderItemResponse buildOrderItemResponse(OrderItem item) {
        ProductVariant variant = item.getProductVariant();

        return OrderItemResponse.builder()
                .id(item.getId())
                .variantId(variant.getId())
                .sku(variant.getSku())
                .productSlug(variant.getProduct().getSlug())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .totalPrice(item.getTotalPrice())
                .build();
    }

}