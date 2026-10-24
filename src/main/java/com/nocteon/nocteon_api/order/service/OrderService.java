package com.nocteon.nocteon_api.order.service;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.nocteon.nocteon_api.address.entity.Address;
import com.nocteon.nocteon_api.address.repository.AddressRepository;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.cart.entity.Cart;
import com.nocteon.nocteon_api.cart.entity.CartItem;
import com.nocteon.nocteon_api.cart.repository.CartItemRepository;
import com.nocteon.nocteon_api.cart.repository.CartRepository;
import com.nocteon.nocteon_api.common.exception.notFound.AddressNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.OrderNotFoundException;
import com.nocteon.nocteon_api.common.exception.payment.OrderNotPayableException;
import com.nocteon.nocteon_api.common.exception.product.CartEmptyException;
import com.nocteon.nocteon_api.common.exception.product.InsufficientStockException;
import com.nocteon.nocteon_api.mail.event.OrderPlacedEvent;
import com.nocteon.nocteon_api.notifications.event.OrderCreatedEvent;
import com.nocteon.nocteon_api.order.dto.request.CreateOrderRequest;
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
import com.nocteon.nocteon_api.storeSettings.dto.response.StoreSettingsResponse;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.persistence.EntityManager;
import java.time.Year;

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
        private final OrderQueryService orderQueryService;
        private final OrderStatusService orderStatusService;
        private final EntityManager entityManager;

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
                        if (variant.getAvailableStock() < cartItem.getQuantity()) {
                                throw new InsufficientStockException(variant.getAvailableStock());
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

                BigDecimal shippingCost = shippingZoneService.calculateShippingCost(address.getCity());

                BigDecimal amountAfterDiscount = total.subtract(discountAmount);
                StoreSettingsResponse storeSettings = storeSettingsService.get();
                boolean qualifiesForFreeShipping = freeShippingFromPromo
                                || (Boolean.TRUE.equals(storeSettings.getIsFreeShippingActive())
                                        && storeSettings.getFreeShippingThreshold() != null
                                        && amountAfterDiscount
                                                .compareTo(storeSettings.getFreeShippingThreshold()) >= 0);

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
                                .orderNumber(generateOrderNumber())
                                .build();

                order = orderRepository.save(order);

                // The first history row: the order was placed. It is written
                // in the same transaction as the order itself, so an order
                // can never exist without the history that explains it.
                orderStatusService.recordCreation(order, initialStatus, "system");

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

                        int reserved = variantRepository.reserveStockIfAvailable(
                                        variant.getId(), cartItem.getQuantity());
                        if (reserved == 0) {
                                // Someone else took the last units between our early check
                                // and this reservation — report what is really left.
                                throw new InsufficientStockException(
                                                variantRepository.findStockById(variant.getId()));
                        }
                }

                // COD orders are confirmed immediately: consume reserved stock.
                if (initialStatus == OrderStatus.CONFIRMED) {
                        for (CartItem cartItem : cart.getItems()) {
                                ProductVariant variant = cartItem.getProductVariant();
                                int oldStock = variantRepository.findStockById(variant.getId());
                                variantRepository.consumeStock(variant.getId(), cartItem.getQuantity());
                                int newStock = variantRepository.findStockById(variant.getId());
                                if (oldStock > variant.getReorderThreshold() && newStock <= variant.getReorderThreshold()) {
                                        eventPublisher.publishEvent(new com.nocteon.nocteon_api.notifications.event.StockChangedEvent(
                                                        variant.getId(), variant.getProduct().getSlug(), variant.getSku(),
                                                        oldStock, newStock, variant.getReorderThreshold()));
                                }
                        }
                }

                cartItemRepository.deleteByCartId(cart.getId());

                // Mark the cart as converted and link it to the new order.
                // The cart row is preserved (not deleted) so staff can trace
                // the checkout history from the carts dashboard.
                cart.setStatus("CONVERTED");
                cart.setConvertedOrderId(order.getId());
                cart.setLastActivityAt(java.time.Instant.now());
                cartRepository.save(cart);

                final Order savedOrder = order;
                final BigDecimal finalTotal = total;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                                eventPublisher.publishEvent(new OrderCreatedEvent(savedOrder.getId(), finalTotal));
                                eventPublisher.publishEvent(new OrderPlacedEvent(savedOrder.getId()));
                        }
                });

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

        @Transactional
        /**
         * Change the status of an order.
         *
         * <p>DELEGATED to {@link OrderStatusService}: there is ONE state
         * machine, and it writes the history row in the same transaction as
         * the change. The rule this method used to apply — "anything is
         * allowed unless the order is delivered or cancelled" — let
         * PENDING jump straight to DELIVERED.
         */
        public OrderResponse updateStatus(Long orderId, OrderStatus newStatus) {
                return orderStatusService.updateStatus(orderId, newStatus, "system");
        }

        /**
         * Generate a unique order number in format ORD-YYYY-NNNNNN.
         * Uses a sequence per year to ensure uniqueness.
         */
        private String generateOrderNumber() {
                Long sequenceValue = (Long) entityManager.createNativeQuery(
                                "SELECT nextval('order_number_seq')")
                                .getSingleResult();
                
                return String.format("ORD-%d-%06d", Year.now().getValue(), sequenceValue);
        }
}
