package com.nocteon.nocteon_api.order.service;


import com.nocteon.nocteon_api.address.repository.AddressRepository;
import com.nocteon.nocteon_api.cart.repository.CartItemRepository;
import com.nocteon.nocteon_api.cart.repository.CartRepository;
import com.nocteon.nocteon_api.common.exception.notFound.OrderNotFoundException;
import com.nocteon.nocteon_api.order.dto.response.OrderResponse;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.order.exception.InvalidOrderStatusTransitionException;

import com.nocteon.nocteon_api.order.event.OrderShippedEvent;
import com.nocteon.nocteon_api.order.repository.OrderItemRepository;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.payment.service.PaymentService;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;
import com.nocteon.nocteon_api.promoCode.service.PromoCodeService;
import com.nocteon.nocteon_api.shippingZone.service.ShippingZoneService;
import com.nocteon.nocteon_api.storeSettings.service.StoreSettingsService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)

class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private PaymentService paymentService;
    @Mock private PromoCodeService promoCodeService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private StoreSettingsService storeSettingsService;
    @Mock private ShippingZoneService shippingZoneService;

    @InjectMocks
    private OrderStatusService orderStatusService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = Order.builder()
                .id(1L)
                .status(OrderStatus.PENDING)
                .items(List.of()) // buildOrderResponse بيلف عليها، فلازم متبقاش null
                .build();
    }

    // ─────────────────────────────────────────────
    // 1. Happy path transitions
    // ─────────────────────────────────────────────

    @Test
    void updateStatus_pendingToConfirmed_succeeds() {
        order.setStatus(OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        OrderResponse response = orderStatusService.updateStatus(1L, OrderStatus.CONFIRMED);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED); // الـ entity اتعدلت فعلا
        verify(orderRepository).save(order);
    }

    @ParameterizedTest
    @MethodSource("validTransitions")
    void updateStatus_allValidTransitions_succeed(OrderStatus from, OrderStatus to) {
        order.setStatus(from);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        OrderResponse response = orderStatusService.updateStatus(1L, to);

        assertThat(response.getStatus()).isEqualTo(to);
        verify(orderRepository).save(order);
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> validTransitions() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(OrderStatus.PENDING, OrderStatus.CONFIRMED),
                org.junit.jupiter.params.provider.Arguments.of(OrderStatus.PENDING, OrderStatus.CANCELLED),
                org.junit.jupiter.params.provider.Arguments.of(OrderStatus.CONFIRMED, OrderStatus.PROCESSING),
                org.junit.jupiter.params.provider.Arguments.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
                org.junit.jupiter.params.provider.Arguments.of(OrderStatus.PROCESSING, OrderStatus.SHIPPED),
                org.junit.jupiter.params.provider.Arguments.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED),
                org.junit.jupiter.params.provider.Arguments.of(OrderStatus.SHIPPED, OrderStatus.DELIVERED));
    }

    // ─────────────────────────────────────────────
    // 2. Invalid transitions (مش موجودة في الـ Set)
    // ─────────────────────────────────────────────

    @Test
    void updateStatus_pendingToShipped_throwsInvalidTransition() {
        order.setStatus(OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderStatusService.updateStatus(1L, OrderStatus.SHIPPED))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        // مهم: نتأكد إن الحالة القديمة اتحفظت زي ما هي ومفيش save حصل
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void updateStatus_confirmedToDelivered_throwsInvalidTransition() {
        order.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderStatusService.updateStatus(1L, OrderStatus.DELIVERED))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
    }

    @Test
    void updateStatus_backwardTransition_confirmedToPending_throwsInvalidTransition() {
        // بيتأكد إن مفيش رجوع للخلف في الـ state machine
        order.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderStatusService.updateStatus(1L, OrderStatus.PENDING))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
    }

    // ─────────────────────────────────────────────
    // 3. Terminal states — DELIVERED و CANCELLED
    // ─────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class)
    void updateStatus_fromDelivered_alwaysThrows(OrderStatus targetStatus) {
        order.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderStatusService.updateStatus(1L, targetStatus))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class)
    void updateStatus_fromCancelled_alwaysThrows(OrderStatus targetStatus) {
        order.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderStatusService.updateStatus(1L, targetStatus))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
    }

    // ─────────────────────────────────────────────
    // 4. Same-status transition (self-loop)
    // ─────────────────────────────────────────────
    // بيوثق السلوك الحالي: الـ Set بتاعت كل status متضمناش نفسها،
    // يعني CONFIRMED -> CONFIRMED برضو بترمي exception.
    // لو ده مش السلوك المطلوب فعليا، ده الـ test اللي هيفشل الأول لما تصلح المنطق.

    @Test
    void updateStatus_sameStatusTransition_currentlyThrows() {
        order.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderStatusService.updateStatus(1L, OrderStatus.CONFIRMED))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
    }

    // ─────────────────────────────────────────────
    // 5. Order not found
    // ─────────────────────────────────────────────

    @Test
    void updateStatus_orderNotFound_throwsOrderNotFoundException() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderStatusService.updateStatus(999L, OrderStatus.CONFIRMED))
                .isInstanceOf(OrderNotFoundException.class);

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    // ─────────────────────────────────────────────
    // 6. Event publishing behavior
    // ─────────────────────────────────────────────

    @Test
    void updateStatus_toShipped_publishesOrderShippedEvent() {
        order.setStatus(OrderStatus.PROCESSING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderStatusService.updateStatus(1L, OrderStatus.SHIPPED);

        ArgumentCaptor<OrderShippedEvent> captor = ArgumentCaptor.forClass(OrderShippedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(order.getId());
    }

    @Test
    void updateStatus_toConfirmed_doesNotPublishShippedEvent() {
        order.setStatus(OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderStatusService.updateStatus(1L, OrderStatus.CONFIRMED);

        verify(eventPublisher, never()).publishEvent(any(OrderShippedEvent.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void updateStatus_toCancelled_doesNotPublishAnyEvent() {
        order.setStatus(OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderStatusService.updateStatus(1L, OrderStatus.CANCELLED);

        verifyNoInteractions(eventPublisher);
    }

    // ─────────────────────────────────────────────
    // 7. buildOrderResponse — التأكد إن الحقول اتنقلت صح
    // ─────────────────────────────────────────────

    @Test
    void updateStatus_responseContainsCorrectOrderFields() {
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(new java.math.BigDecimal("150.00"));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        OrderResponse response = orderStatusService.updateStatus(1L, OrderStatus.CONFIRMED);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("150.00");
        assertThat(response.getItems()).isEmpty();
    }
}