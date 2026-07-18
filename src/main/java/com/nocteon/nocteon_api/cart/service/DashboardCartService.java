package com.nocteon.nocteon_api.cart.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.entity.UserProfile;
import com.nocteon.nocteon_api.cart.dto.request.DashboardCartFilterRequest;
import com.nocteon.nocteon_api.cart.dto.response.DashboardCartItemResponse;
import com.nocteon.nocteon_api.cart.dto.response.DashboardCartResponse;
import com.nocteon.nocteon_api.cart.entity.Cart;
import com.nocteon.nocteon_api.cart.entity.CartItem;
import com.nocteon.nocteon_api.cart.repository.CartRepository;
import com.nocteon.nocteon_api.common.exception.notFound.CartNotFoundException;
import com.nocteon.nocteon_api.common.util.DiscountCalculator;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductMedia;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.repository.ProductMediaRepository;
import com.nocteon.nocteon_api.product.repository.ProductTranslationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardCartService {

    private final CartRepository cartRepository;
    private final ProductTranslationRepository productTranslationRepository;
    private final ProductMediaRepository productMediaRepository;

    @Transactional(readOnly = true)
    public Page<DashboardCartResponse> getAll(DashboardCartFilterRequest filter) {
        return cartRepository
                .findAllForDashboard(filter.getSearch(), true, filter.toPageable())
                .map(this::buildDashboardResponse);
    }

    @Transactional(readOnly = true)
    public DashboardCartResponse getById(Long cartId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(CartNotFoundException::new);
        return buildDashboardResponse(cart);
    }

    private DashboardCartResponse buildDashboardResponse(Cart cart) {
        User user = cart.getUser();
        UserProfile profile = user.getProfile();

        String userEmail = user.getEmail();
        String userFullName = profile != null ? profile.getFullName() : null;

        List<DashboardCartItemResponse> items = cart.getItems().stream()
                .map(this::buildCartItemResponse)
                .toList();

        BigDecimal total = items.stream()
                .map(DashboardCartItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return DashboardCartResponse.builder()
                .id(cart.getId())
                .userId(user.getId())
                .userEmail(userEmail)
                .userFullName(userFullName)
                .items(items)
                .total(total)
                .itemCount(items.size())
                .createdAt(cart.getCreatedAt())
                .updatedAt(cart.getUpdatedAt())
                .build();
    }

    private DashboardCartItemResponse buildCartItemResponse(CartItem item) {
        ProductVariant variant = item.getProductVariant();
        Product product = variant.getProduct();
        String language = LocaleContextHolder.getLocale().getLanguage();

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

        return DashboardCartItemResponse.builder()
                .id(item.getId())
                .variantId(variant.getId())
                .sku(variant.getSku())
                .productSlug(product.getSlug())
                .productName(productName)
                .primaryImageUrl(imageUrl)
                .price(price)
                .compareAtPrice(compareAtPrice)
                .discountPercentage(DiscountCalculator.calculate(price, compareAtPrice))
                .weightGrams(variant.getWeightGrams())
                .grindType(variant.getGrindType())
                .quantity(item.getQuantity())
                .stockQuantity(variant.getStockQuantity())
                .subtotal(subtotal)
                .build();
    }
}
