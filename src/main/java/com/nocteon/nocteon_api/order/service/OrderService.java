package com.nocteon.nocteon_api.order.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.address.entity.Address;
import com.nocteon.nocteon_api.address.repository.AddressRepository;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.cart.entity.Cart;
import com.nocteon.nocteon_api.cart.entity.CartItem;
import com.nocteon.nocteon_api.cart.repository.CartItemRepository;
import com.nocteon.nocteon_api.cart.repository.CartRepository;
import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.exception.notFound.AddressNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.OrderNotFoundException;
import com.nocteon.nocteon_api.common.exception.payment.OrderNotPayableException;
import com.nocteon.nocteon_api.common.exception.product.CartEmptyException;
import com.nocteon.nocteon_api.common.exception.product.InsufficientStockException;
import com.nocteon.nocteon_api.mail.event.OrderPlacedEvent;
import com.nocteon.nocteon_api.notifications.event.OrderCreatedEvent;
import com.nocteon.nocteon_api.order.dto.request.CreateOrderRequest;
import com.nocteon.nocteon_api.order.dto.request.OrderFilterRequest;
import com.nocteon.nocteon_api.order.dto.response.OrderItemResponse;
import com.nocteon.nocteon_api.order.dto.response.OrderPaymentResponse;
import com.nocteon.nocteon_api.order.dto.response.OrderResponse;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.entity.OrderItem;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.order.repository.OrderItemRepository;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.payment.dto.request.PaymobCallbackRequest;
import com.nocteon.nocteon_api.payment.enums.PaymentMethod;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;
import com.nocteon.nocteon_api.payment.service.PaymentService;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;
import com.nocteon.nocteon_api.promoCode.dto.request.PromoCodeCalculationResult;
import com.nocteon.nocteon_api.promoCode.entity.PromoCode;
import com.nocteon.nocteon_api.promoCode.service.PromoCodeService;
import com.nocteon.nocteon_api.shippingZone.service.ShippingZoneService;
import com.nocteon.nocteon_api.storeSettings.service.StoreSettingsService;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

        private final OrderRepository orderRepository;
        private final OrderItemRepository orderItemRepository;
        private final CartRepository cartRepository;
        private final CartItemRepository cartItemRepository;
        private final AddressRepository addressRepository;
        private final ProductVariantRepository variantRepository;
        private final PaymentService paymentService;
        private final PromoCodeService promoCodeService;
        private final ApplicationEventPublisher eventPublisher;
        private final StoreSettingsService storeSettingsService;
        private final ShippingZoneService shippingZoneService;

        @Value("${application.paymob.hmac-secret}")
        private String hmacSecret;

        @Transactional
        public OrderPaymentResponse createOrder(CreateOrderRequest request, UserPrincipal principal) {

                Cart cart = cartRepository.findByUserId(principal.getUserId())
                                .orElseThrow(CartEmptyException::new);

                if (cart.getItems().isEmpty()) {
                        throw new CartEmptyException();
                }

                Address address = addressRepository
                                .findByIdAndUserId(request.getAddressId(), principal.getUserId())
                                .orElseThrow(AddressNotFoundException::new);

                BigDecimal total = BigDecimal.ZERO;
                for (CartItem cartItem : cart.getItems()) {
                        ProductVariant variant = cartItem.getProductVariant();

                        if (!variant.isActive()) {
                                throw new IllegalStateException("Product variant is inactive.");
                        }
                        if (variant.getStockQuantity() < cartItem.getQuantity()) {
                                throw new InsufficientStockException(variant.getStockQuantity());
                        }

                        total = total.add(variant.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
                }

                PromoCode appliedPromoCode = null;
                BigDecimal discountAmount = BigDecimal.ZERO;
                boolean freeShippingFromPromo = false;

                if (request.getPromoCode() != null && !request.getPromoCode().isBlank()) {
                        PromoCodeCalculationResult discountResult = promoCodeService.calculateDiscountForCart(
                                        request.getPromoCode(), cart.getItems(), total, principal.getUserId());

                        appliedPromoCode = promoCodeService.getEntityById(discountResult.getPromoCodeId());
                        discountAmount = discountResult.getDiscountAmount();
                        freeShippingFromPromo = discountResult.isFreeShipping();
                }

                // ─── حساب الشحن ───
                BigDecimal shippingCost = shippingZoneService.calculateShippingCost(address.getCity());

                BigDecimal amountAfterDiscount = total.subtract(discountAmount);
                boolean qualifiesForFreeShipping = freeShippingFromPromo
                                || amountAfterDiscount
                                                .compareTo(storeSettingsService.get().getFreeShippingThreshold()) >= 0;

                if (qualifiesForFreeShipping) {
                        shippingCost = BigDecimal.ZERO;
                }

                OrderStatus initialStatus = request.getPaymentMethod() == PaymentMethod.CASH_ON_DELIVERY
                                ? OrderStatus.CONFIRMED
                                : OrderStatus.PENDING;

                Order order = Order.builder()
                                .user(User.builder().id(principal.getUserId()).build())
                                .address(address)
                                .totalAmount(amountAfterDiscount.add(shippingCost))
                                .shippingCost(shippingCost)
                                .paymentMethod(request.getPaymentMethod())
                                .status(initialStatus)
                                .promoCode(appliedPromoCode)
                                .discountAmount(discountAmount)
                                .notes(request.getNotes())
                                .build();

                order = orderRepository.save(order);

                for (CartItem cartItem : cart.getItems()) {
                        ProductVariant variant = cartItem.getProductVariant();
                        BigDecimal unitPrice = variant.getPrice();

                        orderItemRepository.save(
                                        OrderItem.builder()
                                                        .order(order)
                                                        .productVariant(variant)
                                                        .quantity(cartItem.getQuantity())
                                                        .unitPrice(unitPrice)
                                                        .totalPrice(unitPrice.multiply(
                                                                        BigDecimal.valueOf(cartItem.getQuantity())))
                                                        .build());

                        variant.setStockQuantity(variant.getStockQuantity() - cartItem.getQuantity());
                        variantRepository.save(variant);
                }

                cartItemRepository.deleteByCartId(cart.getId());
                eventPublisher.publishEvent(new OrderCreatedEvent(order.getId(), total));
                eventPublisher.publishEvent(new OrderPlacedEvent(order.getId()));

                if (request.getPaymentMethod() == PaymentMethod.CASH_ON_DELIVERY) {
                        if (appliedPromoCode != null) {
                                promoCodeService.recordRedemption(appliedPromoCode, order, principal.getUserId(),
                                                discountAmount);
                        }

                        log.info("COD Order {} created and confirmed for user {}", order.getId(),
                                        principal.getUserId());

                        return OrderPaymentResponse.builder()
                                        .orderId(order.getId())
                                        .total(order.getTotalAmount())
                                        .paymentUrl(null)
                                        .build();
                }

                String[] names = address.getFullName().trim().split("\\s+", 2);
                String firstName = names[0];
                String lastName = names.length > 1 ? names[1] : ".";

                String paymentUrl = paymentService.initiatePayment(
                                order, firstName, lastName, principal.getUser().getEmail(), address.getPhone());

                log.info("Order {} created for user {}", order.getId(), principal.getUserId());

                return OrderPaymentResponse.builder()
                                .orderId(order.getId())
                                .total(order.getTotalAmount())
                                .paymentUrl(paymentUrl)
                                .build();
        }

        public void handlePaymentWebhook(String hmac, PaymobCallbackRequest payload) {
                paymentService.handleCallback(hmac, payload);
        }

        @Transactional
        public OrderPaymentResponse retryPayment(Long orderId, UserPrincipal principal) {

                Order order = orderRepository.findByIdAndUserId(orderId, principal.getUserId())
                                .orElseThrow(OrderNotFoundException::new);

                if (order.getPaymentStatus() == PaymentStatus.PAID) {
                        throw new OrderNotPayableException("Order is already paid.");
                }

                if (order.getStatus() == OrderStatus.CANCELLED) {
                        throw new OrderNotPayableException("Order is cancelled.");
                }

                Address address = order.getAddress();

                String[] names = address.getFullName().trim().split("\\s+", 2);
                String firstName = names[0];
                String lastName = names.length > 1 ? names[1] : ".";

                String paymentUrl = paymentService.initiatePayment(
                                order,
                                firstName,
                                lastName,
                                principal.getUser().getEmail(),
                                address.getPhone());

                log.info("Payment retry initiated for order {} by user {}", order.getId(), principal.getUserId());

                return OrderPaymentResponse.builder()
                                .orderId(order.getId())
                                .total(order.getTotalAmount())
                                .paymentUrl(paymentUrl)
                                .build();
        }

        public PageResponse<OrderResponse> getUserOrders(UserPrincipal principal,
                        BaseFilterRequest filter) {
                Page<Order> page = orderRepository.findByUserId(
                                principal.getUserId(), filter.toPageable());
                return PageResponse.of(page.map(this::buildResponse));
        }

        public PageResponse<OrderResponse> getAllOrders(OrderFilterRequest filter) {
                Page<Order> page = orderRepository.findAllWithFilters(
                                filter.getStatus(), filter.toPageable());
                return PageResponse.of(page.map(this::buildResponse));
        }

        @Transactional
        public OrderResponse updateStatus(Long orderId, OrderStatus status) {
                Order order = orderRepository.findById(orderId)
                                .orElseThrow(OrderNotFoundException::new);
                order.setStatus(status);
                return buildResponse(orderRepository.save(order));
        }

        private OrderResponse buildResponse(Order order) {
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