package com.nocteon.nocteon_api.cart.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.cart.dto.request.CartItemRequest;
import com.nocteon.nocteon_api.cart.dto.response.CartItemResponse;
import com.nocteon.nocteon_api.cart.dto.response.CartResponse;
import com.nocteon.nocteon_api.cart.entity.Cart;
import com.nocteon.nocteon_api.cart.entity.CartItem;
import com.nocteon.nocteon_api.cart.repository.CartItemRepository;
import com.nocteon.nocteon_api.cart.repository.CartRepository;
import com.nocteon.nocteon_api.common.exception.notFound.CartItemNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.ProductVariantNotFoundException;
import com.nocteon.nocteon_api.common.exception.product.InsufficientStockException;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductMedia;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.repository.ProductMediaRepository;
import com.nocteon.nocteon_api.product.repository.ProductTranslationRepository;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductTranslationRepository productTranslationRepository;
    private final ProductMediaRepository productMediaRepository;

    public CartResponse getCart(UserPrincipal principal) {
        Cart cart = getOrCreateCart(principal.getUserId());
        return buildResponse(cart);
    }

    @Transactional
    public CartResponse addItem(CartItemRequest request, UserPrincipal principal) {
        Cart cart = getOrCreateCart(principal.getUserId());

        ProductVariant variant = variantRepository.findById(request.getVariantId())
                .orElseThrow(() -> new ProductVariantNotFoundException());

        // تحقق من الـ stock
        if (variant.getStock() < request.getQuantity()) {
            throw new InsufficientStockException(variant.getStock());
        }

        // لو الـ item موجود — زود الكمية
        cartItemRepository
                .findByCartIdAndProductVariantId(cart.getId(), variant.getId())
                .ifPresentOrElse(
                        existing -> {
                            int newQty = existing.getQuantity() + request.getQuantity();
                            if (newQty > variant.getStock()) {
                                throw new InsufficientStockException(variant.getStock());
                            }
                            existing.setQuantity(newQty);
                            cartItemRepository.save(existing);
                        },
                        () -> cartItemRepository.save(CartItem.builder()
                                .cart(cart)
                                .productVariant(variant)
                                .quantity(request.getQuantity())
                                .build()));

        return buildResponse(getOrCreateCart(principal.getUserId()));
    }

    @Transactional
    public CartResponse updateItem(Long itemId, int quantity, UserPrincipal principal) {
        Cart cart = getOrCreateCart(principal.getUserId());

        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(CartItemNotFoundException::new);

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new CartItemNotFoundException();
        }

        if (quantity <= 0) {
            cartItemRepository.delete(item);
        } else {
            if (quantity > item.getProductVariant().getStock()) {
                throw new InsufficientStockException(item.getProductVariant().getStock());
            }
            item.setQuantity(quantity);
            cartItemRepository.save(item);
        }

        return buildResponse(getOrCreateCart(principal.getUserId()));
    }

    @Transactional
    public CartResponse removeItem(Long itemId, UserPrincipal principal) {
        Cart cart = getOrCreateCart(principal.getUserId());

        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(CartItemNotFoundException::new);

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new CartItemNotFoundException();
        }

        cartItemRepository.delete(item);
        return buildResponse(getOrCreateCart(principal.getUserId()));
    }

    @Transactional
    public void clearCart(Long userId) {
        Cart cart = getOrCreateCart(userId);
        cartItemRepository.deleteByCartId(cart.getId());
    }

    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> cartRepository.save(
                        Cart.builder()
                                .user(User.builder().id(userId).build())
                                .build()));
    }

    private CartResponse buildResponse(Cart cart) {
        String language = LocaleContextHolder.getLocale().getLanguage();

        List<CartItemResponse> items = cart.getItems().stream()
                .map(item -> {
                    ProductVariant variant = item.getProductVariant();
                    Product product = variant.getProduct();

                    String productName = productTranslationRepository
                            .findByProductIdAndLanguage(product.getId(), language)
                            .map(ProductTranslation::getName)
                            .orElse(product.getSlug());

                    String imageUrl = productMediaRepository
                            .findByProductIdAndIsPrimary(product.getId(), true)
                            .map(ProductMedia::getUrl)
                            .orElse(null);

                    BigDecimal price = variant.getDiscount() != null
                            ? variant.getPrice().multiply(
                                    BigDecimal.ONE.subtract(
                                            variant.getDiscount().divide(BigDecimal.valueOf(100))))
                            : variant.getPrice();

                    BigDecimal subtotal = price.multiply(BigDecimal.valueOf(item.getQuantity()));

                    return CartItemResponse.builder()
                            .id(item.getId())
                            .variantId(variant.getId())
                            .sku(variant.getSku())
                            .productSlug(product.getSlug())
                            .productName(productName)
                            .primaryImageUrl(imageUrl)
                            .price(price)
                            .discount(variant.getDiscount())
                            .quantity(item.getQuantity())
                            .subtotal(subtotal)
                            .build();
                })
                .toList();

        BigDecimal total = items.stream()
                .map(CartItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartResponse.builder()
                .id(cart.getId())
                .items(items)
                .total(total)
                .itemCount(items.size())
                .build();
    }

}
