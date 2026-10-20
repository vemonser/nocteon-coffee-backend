package com.nocteon.nocteon_api.mail.dataProvider;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.nocteon.nocteon_api.mail.enums.EmailType;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

/**
 * The data of the "your order changed" e-mail.
 *
 * <p>The template needs to say what changed and why, so the parameters are
 * the order's NUMBER (what the customer quotes), the status it is now in, the
 * status it was in, and the admin's note.
 */
@Component
@RequiredArgsConstructor
public class OrderStatusChangedDataProvider implements EmailTemplateDataProvider {

    private final OrderRepository orderRepository;

    @Override
    public EmailType supports() {
        return EmailType.ORDER_STATUS_CHANGED;
    }

    @Override
    public Map<String, Object> buildParams(Long orderId) {
        Order order = orderRepository.findById(orderId)
                        .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("customerName", nameOf(order));
        params.put("orderId", order.getId());
        params.put("orderNumber", order.getOrderNumber());
        params.put("orderStatus", order.getStatus() != null ? order.getStatus().name() : null);
        params.put("orderStatusLabel", labelOf(order.getStatus()));
        params.put("totalAmount", order.getTotalAmount());
        return params;
    }

    private static String nameOf(Order order) {
        if (order.getUser() != null && order.getUser().getProfile() != null) {
            String name = order.getUser().getProfile().getFullName();
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return order.getUser() != null ? order.getUser().getEmail() : "";
    }

    private static String labelOf(OrderStatus status) {
        if (status == null) {
            return "";
        }
        // The template renders the word; the API's enum name is what the
        // template's own translation table expects.
        return status.name();
    }
}
