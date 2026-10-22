package com.nocteon.nocteon_api.cart.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.address.entity.Address;
import com.nocteon.nocteon_api.address.repository.AddressRepository;
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
import com.nocteon.nocteon_api.product.mapper.ProductOptionValueMapper;
import com.nocteon.nocteon_api.product.repository.ProductMediaRepository;
import com.nocteon.nocteon_api.product.repository.ProductTranslationRepository;
import com.nocteon.nocteon_api.shippingZone.service.ShippingZoneService;
import com.nocteon.nocteon_api.cart.dto.response.CartStatsResponse;
import com.nocteon.nocteon_api.mail.enums.EmailType;
import com.nocteon.nocteon_api.mail.service.TransactionalEmailService;
import com.nocteon.nocteon_api.storeSettings.service.StoreSettingsService;
import com.nocteon.nocteon_api.storeSettings.dto.response.StoreSettingsResponse;
import com.nocteon.nocteon_api.common.repository.SearchPatterns;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardCartService {

        private final CartRepository cartRepository;
        private final ProductTranslationRepository productTranslationRepository;
        private final ProductMediaRepository productMediaRepository;
        private final ShippingZoneService shippingZoneService;
        private final StoreSettingsService storeSettingsService;
        private final AddressRepository addressRepository;
        private final TransactionalEmailService transactionalEmailService;

        @Transactional(readOnly = true)
        public Page<DashboardCartResponse> getAll(DashboardCartFilterRequest filter) {
                // The search reaches the query as a READY-MADE PATTERN and the
                // date bounds as INSTANTS that are never null: a parameter that
                // only appears in an "is null" test has no type there, and
                // PostgreSQL then fails with "function lower(bytea) does not
                // exist" or "could not determine data type of parameter".
                String search = SearchPatterns.like(filter.getSearch());
                return cartRepository
                                .findAllForDashboard(search, filter.getHasItems(), filter.getStatus(),
                                                SearchPatterns.from(filter.getFrom()),
                                                SearchPatterns.to(filter.getTo()),
                                                filter.toPageable())
                                .map(this::buildDashboardResponse);
        }

        /**
         * One cart, by id, for staff.
         *
         * <p>Reached only through {@code cart:read_all}, which no customer holds. The permission
         * is the authorisation; this method is the reason it is worth having — it returns another
         * user's email, full name and basket contents, so it must never be reachable by guessing an
         * id. Any future refactor that re-gates this on {@code cart:manage} reopens the hole,
         * which is why the permission name is deliberately not the one the storefront uses.
         */
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
                String avatarUrl = profile != null ? profile.getAvatarUrl() : null;

                List<DashboardCartItemResponse> items = cart.getItems().stream()
                                .map(this::buildCartItemResponse)
                                .toList();

                BigDecimal subtotal = items.stream()
                                .map(DashboardCartItemResponse::getSubtotal)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                int totalUnits = items.stream()
                                .mapToInt(DashboardCartItemResponse::getQuantity)
                                .sum();

                // Get store settings for free-shipping threshold and currency
                StoreSettingsResponse storeSettings = storeSettingsService.get();
                BigDecimal freeShippingThreshold = storeSettings.getFreeShippingThreshold();
                String currency = storeSettings.getCurrency();
                boolean freeShippingActive = Boolean.TRUE.equals(storeSettings.getIsFreeShippingActive());

                // Estimate shipping from user's default address
                BigDecimal estimatedShipping = BigDecimal.ZERO;
                boolean qualifiesForFreeShipping = false;
                if (freeShippingActive && freeShippingThreshold != null) {
                        Address defaultAddress = addressRepository
                                        .findByUserIdAndIsDefault(user.getId(), true)
                                        .orElse(null);
                        if (defaultAddress != null && defaultAddress.getCity() != null) {
                                try {
                                        estimatedShipping = shippingZoneService
                                                        .calculateShippingCost(defaultAddress.getCity());
                                        qualifiesForFreeShipping = subtotal.compareTo(freeShippingThreshold) >= 0;
                                        if (qualifiesForFreeShipping) {
                                                estimatedShipping = BigDecimal.ZERO;
                                        }
                                } catch (Exception e) {
                                        log.debug("Could not estimate shipping for cart {}: {}", cart.getId(),
                                                        e.getMessage());
                                }
                        }
                }

                return DashboardCartResponse.builder()
                                .id(cart.getId())
                                .userId(user.getId())
                                .userEmail(userEmail)
                                .userFullName(userFullName)
                                .avatarUrl(avatarUrl)
                                .items(items)
                                .total(subtotal)
                                .itemCount(items.size())
                                .totalUnits(totalUnits)
                                .createdAt(cart.getCreatedAt())
                                .updatedAt(cart.getUpdatedAt())
                                .lastActivityAt(cart.getLastActivityAt())
                                .lastRemindedAt(cart.getLastRemindedAt())
                                .status(cart.getStatus())
                                .convertedOrderId(cart.getConvertedOrderId())
                                .estimatedShipping(estimatedShipping)
                                .qualifiesForFreeShipping(qualifiesForFreeShipping)
                                .freeShippingThreshold(freeShippingThreshold)
                                .currency(currency)
                                .hasItems(!items.isEmpty())
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

                int availableStock = variant.getAvailableStock();
                boolean outOfStock = availableStock <= 0;
                boolean lowStock = availableStock > 0 && availableStock <= variant.getReorderThreshold();

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
                                .optionValues(ProductOptionValueMapper.buildSummaries(variant, language))
                                .quantity(item.getQuantity())
                                .stockQuantity(variant.getStockQuantity())
                                .availableStock(availableStock)
                                .subtotal(subtotal)
                                .productIsActive(product.isActive())
                                .variantIsActive(variant.isActive())
                                .outOfStock(outOfStock)
                                .lowStock(lowStock)
                                .build();
        }

        /**
         * Aggregate stats for the carts dashboard KPI strip.
         * All counts are computed server-side so the frontend never has to guess.
         */
        @Transactional(readOnly = true)
        public CartStatsResponse getStats() {
                long activeCarts = cartRepository.countByStatus("ACTIVE");
                long abandonedCarts = cartRepository.countByStatus("ABANDONED");
                long convertedCarts = cartRepository.countByStatus("CONVERTED");

                // Total potential revenue = sum of subtotals of ACTIVE + ABANDONED carts
                BigDecimal totalPotentialRevenue = cartRepository.sumSubtotalsByStatuses(
                                List.of("ACTIVE", "ABANDONED"));
                if (totalPotentialRevenue == null) {
                        totalPotentialRevenue = BigDecimal.ZERO;
                }

                long totalRelevantCarts = activeCarts + abandonedCarts;
                BigDecimal averageCartValue = BigDecimal.ZERO;
                if (totalRelevantCarts > 0) {
                        averageCartValue = totalPotentialRevenue
                                        .divide(BigDecimal.valueOf(totalRelevantCarts), 2,
                                                        java.math.RoundingMode.HALF_UP);
                }

                return CartStatsResponse.builder()
                                .activeCarts(activeCarts)
                                .abandonedCarts(abandonedCarts)
                                .convertedCarts(convertedCarts)
                                .totalPotentialRevenue(totalPotentialRevenue)
                                .averageCartValue(averageCartValue)
                                .build();
        }

        /**
         * Send an abandonment reminder e-mail for a single cart on demand.
         *
         * <p>Uses a distinct idempotency key (suffixed with {@code -MANUAL}) so it does
         * not collide with the scheduled job's daily key. Returns true if the e-mail was
         * enqueued, false if the cart was not found or had no items.
         */
        @Transactional
        public boolean sendReminder(Long cartId) {
                Cart cart = cartRepository.findById(cartId).orElse(null);
                if (cart == null || cart.getItems().isEmpty()) {
                        return false;
                }

                // Use a distinct idempotency key so this manual send does not collide
                // with the scheduled job's "one per day per cart" key.
                String idempotencyKey = "CART-" + cartId + "-ABANDONED-MANUAL-"
                                + java.util.UUID.randomUUID().toString().substring(0, 8);

                transactionalEmailService.enqueueEmail(
                                idempotencyKey,
                                EmailType.ABANDONED_CART,
                                cart.getUser().getEmail(),
                                "CART",
                                cartId);
                return true;
        }
}