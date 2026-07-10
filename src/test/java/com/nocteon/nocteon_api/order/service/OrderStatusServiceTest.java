package com.nocteon.nocteon_api.order.service;

import com.nocteon.nocteon_api.common.exception.notFound.OrderNotFoundException;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.order.exception.InvalidOrderStatusTransitionException;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Edge-case coverage for OrderStatusService's state machine: valid transitions,
 * rejected transitions, and terminal-state immutability.
 */
@ExtendWith(MockitoExtension.class)
class OrderStatusServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderStatusService orderStatusService;

    @Test
    void throwsWhenOrderDoesNotExist() {
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderStatusService.updateStatus(1L, OrderStatus.CONFIRMED))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({
            "PENDING, CONFIRMED",
            "PENDING, CANCELLED",
            "CONFIRMED, PROCESSING",
            "CONFIRMED, CANCELLED",
            "PROCESSING, SHIPPED",
            "PROCESSING, CANCELLED",
            "SHIPPED, DELIVERED"
    })
    void allowsValidTransitions(OrderStatus from, OrderStatus to) {
        Order order = Order.builder().id(1L).status(from).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = orderStatusService.updateStatus(1L, to);

        assertThat(response.getStatus()).isEqualTo(to);
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @CsvSource({
            // Terminal states can never move again
            "DELIVERED, PROCESSING",
            "DELIVERED, CANCELLED",
            "DELIVERED, PENDING",
            "CANCELLED, CONFIRMED",
            "CANCELLED, PENDING",
            // Cannot skip stages backward
            "SHIPPED, PENDING",
            "SHIPPED, CONFIRMED",
            "PROCESSING, PENDING",
            "PROCESSING, CONFIRMED",
            // Cannot skip stages forward past the immediate next allowed state
            "PENDING, SHIPPED",
            "PENDING, DELIVERED",
            "CONFIRMED, DELIVERED",
            "CONFIRMED, SHIPPED"
    })
    void rejectsInvalidTransitions(OrderStatus from, OrderStatus to) {
        Order order = Order.builder().id(1L).status(from).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderStatusService.updateStatus(1L, to))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void publishesOrderShippedEventOnlyWhenTransitioningToShipped() {
        Order order = Order.builder().id(1L).status(OrderStatus.PROCESSING).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderStatusService.updateStatus(1L, OrderStatus.SHIPPED);

        verify(eventPublisher).publishEvent(any(com.nocteon.nocteon_api.order.event.OrderShippedEvent.class));
    }

    @Test
    void doesNotPublishShippedEventForOtherTransitions() {
        Order order = Order.builder().id(1L).status(OrderStatus.PENDING).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderStatusService.updateStatus(1L, OrderStatus.CONFIRMED);

        verify(eventPublisher, never()).publishEvent(any(com.nocteon.nocteon_api.order.event.OrderShippedEvent.class));
    }

    @Test
    void sameStatusToItselfIsRejectedAsNoOpTransition() {
        // Not in ALLOWED_TRANSITIONS map's value set for any state -> should be rejected,
        // preventing accidental no-op saves and event republishing.
        Order order = Order.builder().id(1L).status(OrderStatus.CONFIRMED).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderStatusService.updateStatus(1L, OrderStatus.CONFIRMED))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
    }
}