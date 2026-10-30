package com.nocteon.nocteon_api.order.service;

import com.nocteon.nocteon_api.common.exception.notFound.OrderNotFoundException;
import com.nocteon.nocteon_api.order.dto.request.UpdateOrderStatusRequest;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.entity.OrderStatusHistory;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.order.exception.CancellationReasonRequiredException;
import com.nocteon.nocteon_api.order.exception.InvalidOrderStatusTransitionException;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.order.repository.OrderStatusHistoryRepository;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;
import com.nocteon.nocteon_api.product.service.InventoryMovementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The order's status machine.
 *
 * <p>Three things are proved here: the transitions are the ones the state
 * machine allows, the history row is written WITH the change (never without
 * it), and a cancellation has to say why.
 */
@ExtendWith(MockitoExtension.class)
class OrderStatusServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderStatusHistoryRepository historyRepository;
    @Mock private OrderQueryService orderQueryService;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private InventoryMovementService inventoryMovementService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @org.mockito.Mock
    private com.nocteon.nocteon_api.audit.service.AuditLogService auditLogService;

    @InjectMocks
    private OrderStatusService orderStatusService;

    private UpdateOrderStatusRequest request(OrderStatus status) {
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(status);
        if (status == OrderStatus.CANCELLED) {
            // A cancellation has to say why.
            request.setReason("Customer changed their mind");
        }
        return request;
    }

    @Test
    void throwsWhenOrderDoesNotExist() {
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderStatusService.updateStatus(1L, request(OrderStatus.CONFIRMED), "admin"))
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

        orderStatusService.updateStatus(1L, request(to), "admin");

        assertThat(order.getStatus()).isEqualTo(to);
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

        assertThatThrownBy(() -> orderStatusService.updateStatus(1L, request(to), "admin"))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        verify(orderRepository, never()).save(any());
        // And no history row: the change never happened.
        verify(historyRepository, never()).save(any());
    }

    @Test
    void theRejectedTransitionSaysWhatItRefused() {
        Order order = Order.builder().id(1L).status(OrderStatus.DELIVERED).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderStatusService.updateStatus(1L, request(OrderStatus.PENDING), "admin"))
                .isInstanceOf(InvalidOrderStatusTransitionException.class)
                .satisfies(thrown -> {
                    InvalidOrderStatusTransitionException ex = (InvalidOrderStatusTransitionException) thrown;
                    assertThat(ex.getFrom()).isEqualTo(OrderStatus.DELIVERED);
                    assertThat(ex.getTo()).isEqualTo(OrderStatus.PENDING);
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                });
    }

    @Test
    void writesTheHistoryRowWithTheChange() {
        Order order = Order.builder().id(1L).status(OrderStatus.PENDING).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateOrderStatusRequest request = request(OrderStatus.CONFIRMED);
        request.setNote("Paid by card");
        request.setNotifyCustomer(true);

        orderStatusService.updateStatus(1L, request, "admin@aurum.local");

        ArgumentCaptor<OrderStatusHistory> captor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository).save(captor.capture());
        OrderStatusHistory history = captor.getValue();
        assertThat(history.getFromStatus()).isEqualTo("PENDING");
        assertThat(history.getToStatus()).isEqualTo("CONFIRMED");
        assertThat(history.getNote()).isEqualTo("Paid by card");
        assertThat(history.getChangedBy()).isEqualTo("admin@aurum.local");
        assertThat(history.isNotifyCustomer()).isTrue();
    }

    @Test
    void aCancellationNeedsAReason() {
        Order order = Order.builder().id(1L).status(OrderStatus.PENDING).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        // A cancellation with NO reason: the request is built by hand, because
        // the helper adds one.
        UpdateOrderStatusRequest bare = new UpdateOrderStatusRequest();
        bare.setStatus(OrderStatus.CANCELLED);

        assertThatThrownBy(() -> orderStatusService.updateStatus(1L, bare, "admin"))
                .isInstanceOf(CancellationReasonRequiredException.class);

        verify(historyRepository, never()).save(any());
    }

    @Test
    void aCancellationKeepsItsReason() {
        Order order = Order.builder().id(1L).status(OrderStatus.PENDING).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateOrderStatusRequest request = request(OrderStatus.CANCELLED);
        request.setReason("Customer changed their mind");

        orderStatusService.updateStatus(1L, request, "admin");

        assertThat(order.getCancellationReason()).isEqualTo("Customer changed their mind");
    }

    @Test
    void stampsTheShipmentWhenTheOrderIsShipped() {
        Order order = Order.builder().id(1L).status(OrderStatus.PROCESSING).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderStatusService.updateStatus(1L, request(OrderStatus.SHIPPED), "admin");

        assertThat(order.getShippedAt()).isNotNull();
        assertThat(order.getDeliveredAt()).isNull();
    }

    @Test
    void stampsTheDeliveryWhenTheOrderIsDelivered() {
        Order order = Order.builder().id(1L).status(OrderStatus.SHIPPED).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderStatusService.updateStatus(1L, request(OrderStatus.DELIVERED), "admin");

        assertThat(order.getDeliveredAt()).isNotNull();
    }

    @Test
    void publishesOrderShippedEventOnlyWhenTransitioningToShipped() {
        Order order = Order.builder().id(1L).status(OrderStatus.PROCESSING).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderStatusService.updateStatus(1L, request(OrderStatus.SHIPPED), "admin");

        verify(eventPublisher).publishEvent(any(com.nocteon.nocteon_api.order.event.OrderShippedEvent.class));
    }

    @Test
    void doesNotPublishShippedEventForOtherTransitions() {
        Order order = Order.builder().id(1L).status(OrderStatus.PENDING).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderStatusService.updateStatus(1L, request(OrderStatus.CONFIRMED), "admin");

        verify(eventPublisher, never()).publishEvent(any(com.nocteon.nocteon_api.order.event.OrderShippedEvent.class));
    }

    @Test
    void aCancellationRestocksWhatTheLedgerSaysTheOrderConsumed() {
        Order order = Order.builder().id(1L).status(OrderStatus.CONFIRMED).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        // The ledger is the authority: it reports 3 units out of variant 7.
        when(inventoryMovementService.consumedByOrder(1L)).thenReturn(Map.of(7L, 3));

        orderStatusService.updateStatus(1L, request(OrderStatus.CANCELLED), "admin@nocteon.test");

        verify(inventoryMovementService).restockForCancelledOrder(
                7L, 3, "Customer changed their mind", "admin@nocteon.test", 1L);
    }

    @Test
    void aCancellationThatConsumedNoStockRestocksNothing() {
        Order order = Order.builder().id(1L).status(OrderStatus.PENDING).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        // An unpaid order only reserved its units: the ledger has nothing to mirror.
        when(inventoryMovementService.consumedByOrder(1L)).thenReturn(Map.of());

        orderStatusService.updateStatus(1L, request(OrderStatus.CANCELLED), "admin");

        verify(inventoryMovementService, never())
                .restockForCancelledOrder(anyLong(), anyInt(), any(), any(), any());
    }

    @Test
    void aNoOpChangeIsRefused() {
        // The order is already in that status. The state machine refuses it:
        // a no-op that answers 200 hides a client that sent the wrong status,
        // and it would write a history row that says nothing happened.
        Order order = Order.builder().id(1L).status(OrderStatus.CONFIRMED).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderStatusService.updateStatus(1L, request(OrderStatus.CONFIRMED), "admin"))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        verify(orderRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
