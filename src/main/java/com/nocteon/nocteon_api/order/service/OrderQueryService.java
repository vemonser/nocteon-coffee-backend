package com.nocteon.nocteon_api.order.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.order.dto.request.OrderFilterRequest;
import com.nocteon.nocteon_api.order.dto.response.OrderItemResponse;
import com.nocteon.nocteon_api.order.dto.response.OrderResponse;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderQueryService {
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getUserOrders(UserPrincipal principal, BaseFilterRequest filter) {
        Page<Order> page = orderRepository.findByUserId(
                principal.getUserId(), filter.toPageable());
        return PageResponse.of(page.map(this::buildResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getAllOrders(OrderFilterRequest filter) {
        Page<Order> page = orderRepository.findAllWithFilters(
                filter.getStatus(), filter.toPageable());
        return PageResponse.of(page.map(this::buildResponse));
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new com.nocteon.nocteon_api.common.exception.notFound.OrderNotFoundException());
        return buildResponse(order);
    }

    OrderResponse buildResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .variantId(item.getProductVariant().getId())
                        .sku(item.getProductVariant().getSku())
                        .productSlug(item.getProductVariant().getProduct().getSlug())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .totalPrice(item.getTotalPrice())
                        .build())
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .paymentStatus(order.getPaymentStatus())
                .notes(order.getNotes())
                .items(items)
                .createdAt(order.getCreatedAt())
                .build();
    }
}
