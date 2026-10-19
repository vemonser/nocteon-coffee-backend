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
import com.nocteon.nocteon_api.common.util.DiscountCalculator;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductMedia;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.mapper.ProductOptionValueMapper;
import com.nocteon.nocteon_api.product.repository.ProductMediaRepository;
import com.nocteon.nocteon_api.product.repository.ProductTranslationRepository;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;

import org.springframework.transaction.annotation.Transactional;
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

    private static final int LOW_STOCK_THRESHOLD = 5;

    @Transactional(readOnly = true)
    public CartResponse getCart(UserPrincipal principal) {
        Cart cart = getOrCreateCart(principal.getUserId());
        return buildResponse(cart);
    }

    @Transactional
    public CartResponse addItem(CartItemRequest request, UserPrincipal principal) {
        Cart cart = getOrCreateCart(principal.getUserId());

        ProductVariant variant = variantRepository.findById(request.getVariantId())
                .orElseThrow(() -> new ProductVariantNotFoundException());

        if (!variant.isActive()) {
            throw new ProductVariantNotFoundException();
        }
        if (variant.getStockQuantity() < request.getQuantity()) {
            throw new InsufficientStockException(variant.getStockQuantity());
        }

        // For low-stock items, attempt atomic reservation to prevent race with checkout
        if (variant.getStockQuantity() <= LOW_STOCK_THRESHOLD) {
            int reserved = variantRepository.decrementStockIfAvailable(
                    variant.getId(), request.getQuantity());
            if (reserved == 0) {
                throw new InsufficientStockException(variantRepository.findStockById(variant.getId()));
            }
            // Mark that this cart item has reserved stock (negative quantity convention in reserved field)
            // We'll track this via a separate mechanism - for now just reserve and release on removal
            log.debug("Reserved {} units of variant {} for cart {}", request.getQuantity(), variant.getId(), cart.getId());
        }

        cartItemRepository
                .findByCartIdAndProductVariantId(cart.getId(), variant.getId())
                .ifPresentOrElse(
                        existing -> {
                            int newQty = existing.getQuantity() + request.getQuantity();
                            if (newQty > variant.getStockQuantity()) {
                                throw new InsufficientStockException(variant.getStockQuantity());
                            }
                            existing.setQuantity(newQty);
                            cartItemRepository.save(existing);
                        },
                        () -> cartItemRepository.save(CartItem.builder()
                                .cart(cart)
                                .productVariant(variant)
                                .quantity(request.getQuantity())
                                .build()));

        return buildResponse(cart);
    }

    @Transactional
    public CartResponse updateItem(Long itemId, int quantity, UserPrincipal principal) {
        Cart cart = getOrCreateCart(principal.getUserId());

        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(CartItemNotFoundException::new);

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new CartItemNotFoundException();
        }

        ProductVariant variant = item.getProductVariant();

        if (quantity <= 0) {
            // Release reserved stock if this was a low-stock item
            if (variant.getStockQuantity() <= LOW_STOCK_THRESHOLD) {
                variantRepository.incrementStock(variant.getId(), item.getQuantity());
                log.debug("Released {} units of variant {} from cart {}", item.getQuantity(), variant.getId(), cart.getId());
            }
            cartItemRepository.delete(item);
        } else {
            int delta = quantity - item.getQuantity();
            if (delta > 0) {
                if (quantity > variant.getStockQuantity()) {
                    throw new InsufficientStockException(variant.getStockQuantity());
                }
                // For low-stock items, reserve additional stock
                if (variant.getStockQuantity() <= LOW_STOCK_THRESHOLD) {
                    int reserved = variantRepository.decrementStockIfAvailable(variant.getId(), delta);
                    if (reserved == 0) {
                        throw new InsufficientStockException(variantRepository.findStockById(variant.getId()));
                    }
                    log.debug("Reserved additional {} units of variant {} for cart {}", delta, variant.getId(), cart.getId());
                }
            } else if (delta < 0) {
                // Release excess reserved stock for low-stock items
                if (variant.getStockQuantity() <= LOW_STOCK_THRESHOLD) {
                    variantRepository.incrementStock(variant.getId(), -delta);
                    log.debug("Released {} units of variant {} from cart {}", -delta, variant.getId(), cart.getId());
                }
            }
            item.setQuantity(quantity);
            cartItemRepository.save(item);
        }

        return buildResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(Long itemId, UserPrincipal principal) {
        Cart cart = getOrCreateCart(principal.getUserId());

        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(CartItemNotFoundException::new);

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new CartItemNotFoundException();
        }

        ProductVariant variant = item.getProductVariant();

        // Release reserved stock for low-stock items
        if (variant.getStockQuantity() <= LOW_STOCK_THRESHOLD) {
            variantRepository.incrementStock(variant.getId(), item.getQuantity());
            log.debug("Released {} units of variant {} from cart {}", item.getQuantity(), variant.getId(), cart.getId());
        }

        cartItemRepository.delete(item);
        return buildResponse(cart);
    }

    @Transactional
    public void clearCart(Long userId) {
        Cart cart = getOrCreateCart(userId);
        // Release any reserved stock for low-stock items
        for (CartItem item : cart.getItems()) {
            ProductVariant variant = item.getProductVariant();
            if (variant.getStockQuantity() <= LOW_STOCK_THRESHOLD) {
                variantRepository.incrementStock(variant.getId(), item.getQuantity());
                log.debug("Cleared cart: released {} units of variant {}", item.getQuantity(), variant.getId());
            }
        }
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

                    BigDecimal price = variant.getPrice();
                    BigDecimal compareAtPrice = variant.getCompareAtPrice();

                    BigDecimal subtotal = price.multiply(BigDecimal.valueOf(item.getQuantity()));

                    return CartItemResponse.builder()
                            .id(item.getId())
                            .variantId(variant.getId())
                            .sku(variant.getSku())
                            .productSlug(product.getSlug())
                            .optionValues(ProductOptionValueMapper.buildSummaries(variant, language))
                            .productName(productName)
                            .primaryImageUrl(imageUrl)
                            .price(price)
                            .compareAtPrice(compareAtPrice)
                            .discountPercentage(DiscountCalculator.calculate(price, compareAtPrice))
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
