package com.nocteon.nocteon_api.mail.dataProvider;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.nocteon.nocteon_api.mail.enums.EmailType;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderShippedDataProvider implements EmailTemplateDataProvider {

    private final OrderRepository orderRepository;

    @Override
    public EmailType supports() {
        return EmailType.ORDER_SHIPPED;
    }

    @Override
    public Map<String, Object> buildParams(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));

        return Map.of(
                "customerName", order.getUser().getProfile().getFullName(),
                "orderId", order.getId()
        );
    }
}