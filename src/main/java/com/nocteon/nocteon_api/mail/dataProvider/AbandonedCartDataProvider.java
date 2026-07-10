package com.nocteon.nocteon_api.mail.dataProvider;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.nocteon.nocteon_api.cart.entity.Cart;
import com.nocteon.nocteon_api.cart.entity.CartItem;
import com.nocteon.nocteon_api.cart.repository.CartRepository;
import com.nocteon.nocteon_api.mail.enums.EmailType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AbandonedCartDataProvider implements EmailTemplateDataProvider {

    private final CartRepository cartRepository;
    private static final String DEFAULT_LANGUAGE = "en";

    @Override
    public EmailType supports() {
        return EmailType.ABANDONED_CART;
    }

    @Override
    public Map<String, Object> buildParams(Long cartId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new IllegalStateException("Cart not found: " + cartId));

        return Map.of(
                "customerName", cart.getUser().getProfile().getFullName(),
                "items", cart.getItems().stream()
                        .map(this::mapItem)
                        .toList());
    }

    private Map<String, Object> mapItem(CartItem item) {
        String productName = item.getProductVariant().getProduct().getTranslations().stream()
                .filter(t -> DEFAULT_LANGUAGE.equals(t.getLanguage())) 
                .findFirst()
                .map( t -> t.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "No English translation found for product variant: " + item.getProductVariant().getId()));

        return Map.of(
                "name", productName,
                "quantity", item.getQuantity());
    }
}