package com.nocteon.nocteon_api.mail.dataProvider;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.nocteon.nocteon_api.mail.enums.EmailType;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.entity.OrderItem;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderConfirmationDataProvider implements EmailTemplateDataProvider {

    private final OrderRepository orderRepository;

    @Override
    public EmailType supports() {
        return EmailType.ORDER_CONFIRMATION;
    }

    /**
     * The variables the provider-side template fills in.
     *
     * <p>Null-safe on purpose (FE-16): a resend runs through here on demand, and
     * an account without a profile, or a line whose product has no English
     * translation, must not turn "send it again" into a 500. A missing full name
     * falls back to the username, and {@code HashMap} rather than
     * {@code Map.of} because {@code Map.of} rejects null values outright.
     */
    @Override
    public Map<String, Object> buildParams(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));

        Map<String, Object> params = new HashMap<>();
        params.put("customerName", customerName(order));
        params.put("recipientEmail", order.getUser() != null ? order.getUser().getEmail() : null);
        params.put("orderId", order.getId());
        params.put("orderNumber", order.getOrderNumber());
        params.put("total", order.getTotalAmount() != null ? order.getTotalAmount().toString() : "0.00");
        params.put("items", order.getItems().stream().map(this::mapItem).toList());
        return params;
    }

    private String customerName(Order order) {
        if (order.getUser() != null && order.getUser().getProfile() != null) {
            String fullName = order.getUser().getProfile().getFullName();
            if (fullName != null && !fullName.isBlank()) {
                return fullName;
            }
        }
        if (order.getAddress() != null && order.getAddress().getFullName() != null) {
            return order.getAddress().getFullName();
        }
        return order.getUser() != null ? order.getUser().getUsername() : "";
    }

    /**
     * One line of the e-mail: the product's name, not its slug.
     *
     * <p>The slug was what this used to send — a customer reading the
     * confirmation saw "ethiopia-yirgacheffe-natural" where the invoice says
     * "Ethiopia Yirgacheffe Natural".
     */
    private Map<String, Object> mapItem(OrderItem item) {
        Map<String, Object> line = new HashMap<>();
        line.put("name", productName(item));
        line.put("sku", item.getProductVariant() != null ? item.getProductVariant().getSku() : null);
        line.put("quantity", item.getQuantity());
        line.put("unitPrice", item.getUnitPrice() != null ? item.getUnitPrice().toString() : null);
        line.put("totalPrice", item.getTotalPrice() != null ? item.getTotalPrice().toString() : null);
        return line;
    }

    private String productName(OrderItem item) {
        if (item.getProductVariant() == null || item.getProductVariant().getProduct() == null) {
            return "";
        }
        var translations = item.getProductVariant().getProduct().getTranslations();
        if (translations == null || translations.isEmpty()) {
            return item.getProductVariant().getProduct().getSlug();
        }
        return translations.stream()
                .filter(t -> "en".equalsIgnoreCase(t.getLanguage()))
                .map(ProductTranslation::getName)
                .findFirst()
                .orElseGet(() -> translations.iterator().next().getName());
    }
}
