package com.nocteon.nocteon_api.order.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
import com.nocteon.nocteon_api.common.exception.product.CartEmptyException;
import com.nocteon.nocteon_api.common.exception.product.InsufficientStockException;
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
import com.nocteon.nocteon_api.payment.service.PaymentService;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;

import jakarta.transaction.Transactional;
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

        @Transactional
        public OrderPaymentResponse createOrder(CreateOrderRequest request,
                        UserPrincipal principal) {

                // 1. جيب الـ Cart
                Cart cart = cartRepository.findByUserId(principal.getUserId())
                                .orElseThrow(CartEmptyException::new);

                if (cart.getItems().isEmpty()) {
                        throw new CartEmptyException();
                }

                // 2. جيب الـ Address
                Address address = addressRepository
                                .findByIdAndUserId(request.getAddressId(), principal.getUserId())
                                .orElseThrow(AddressNotFoundException::new);

                // 3. احسب الـ total وتحقق من الـ stock
                BigDecimal total = BigDecimal.ZERO;
                for (CartItem cartItem : cart.getItems()) {
                        ProductVariant variant = cartItem.getProductVariant();

                        if (variant.getStock() < cartItem.getQuantity()) {
                                throw new InsufficientStockException(variant.getStock());
                        }

                        BigDecimal price = variant.getDiscount() != null
                                        ? variant.getPrice().multiply(
                                                        BigDecimal.ONE.subtract(
                                                                        variant.getDiscount().divide(
                                                                                        BigDecimal.valueOf(100))))
                                        : variant.getPrice();

                        total = total.add(price.multiply(BigDecimal.valueOf(cartItem.getQuantity())));
                }

                // 4. Create Order
                Order order = Order.builder()
                                .user(User.builder().id(principal.getUserId()).build())
                                .address(address)
                                .status(OrderStatus.PAYMENT_PENDING)
                                .totalAmount(total)
                                .notes(request.getNotes())
                                .build();

                order = orderRepository.save(order);

                // 5. Save Order Items + خصم الـ stock
                final Order savedOrder = order;
                for (CartItem cartItem : cart.getItems()) {
                        ProductVariant variant = cartItem.getProductVariant();

                        BigDecimal price = variant.getDiscount() != null
                                        ? variant.getPrice().multiply(
                                                        BigDecimal.ONE.subtract(
                                                                        variant.getDiscount().divide(
                                                                                        BigDecimal.valueOf(100))))
                                        : variant.getPrice();

                        orderItemRepository.save(OrderItem.builder()
                                        .order(savedOrder)
                                        .productVariant(variant)
                                        .quantity(cartItem.getQuantity())
                                        .unitPrice(price)
                                        .totalPrice(price.multiply(BigDecimal.valueOf(cartItem.getQuantity())))
                                        .build());

                        // خصم من الـ stock
                        variant.setStock(variant.getStock() - cartItem.getQuantity());
                        variantRepository.save(variant);
                }

                // 6. Create Payment — Moyasar
                String firstName = address.getFullName().split(" ")[0];
                String lastName = address.getFullName().split(" ").length > 1
                                ? address.getFullName().split(" ")[1]
                                : ".";

                String paymentUrl = paymentService.initiatePayment(
                                order, firstName, lastName,
                                principal.getUser().getEmail(),
                                address.getPhone());

                // 7. Clear Cart
                cartItemRepository.deleteByCartId(cart.getId());

                log.info("Order {} created for user {}", order.getId(), principal.getUserId());

                return OrderPaymentResponse.builder()
                                .orderId(order.getId())
                                .total(total)
                                .paymentUrl(paymentUrl)
                                .build();
        }

        // @Transactional
        // public void handlePaymentCallback(String paymentId, Long orderId) {
        // Order order = orderRepository.findById(orderId)
        // .orElseThrow(OrderNotFoundException::new);

        // boolean paid = paymentService.verifyCallback(orderId,paymentId);

        // if (paid) {
        // order.setStatus(OrderStatus.PAID);
        // order.setPaymentId(paymentId);
        // order.setPaymentStatus("paid");
        // } else {
        // order.setStatus(OrderStatus.CANCELLED);
        // order.setPaymentStatus("failed");

        // // رجّع الـ stock
        // order.getItems().forEach(item -> {
        // ProductVariant variant = item.getProductVariant();
        // variant.setStock(variant.getStock() + item.getQuantity());
        // variantRepository.save(variant);
        // });
        // }

        // orderRepository.save(order);
        // }

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

        public void handlePaymentWebhook(Map<String, String> params) {
                paymentService.handleCallback(params);
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
                                // .paymentStatus(order.getPaymentStatus())
                                .notes(order.getNotes())
                                .items(items)
                                .createdAt(order.getCreatedAt())
                                .build();
        }
}