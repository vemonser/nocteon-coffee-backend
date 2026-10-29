package com.nocteon.nocteon_api.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nocteon.nocteon_api.common.exception.notFound.OrderNotFoundException;
import com.nocteon.nocteon_api.order.dto.request.CreateRefundRequest;
import com.nocteon.nocteon_api.order.dto.response.OrderRefundResponse;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.entity.OrderRefund;
import com.nocteon.nocteon_api.order.exception.InvalidRefundException;
import com.nocteon.nocteon_api.order.repository.OrderRefundRepository;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.payment.entity.Payment;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;
import com.nocteon.nocteon_api.payment.exception.RefundFailedException;
import com.nocteon.nocteon_api.payment.repository.PaymentRepository;
import com.nocteon.nocteon_api.payment.service.PaymentService;

/**
 * The refunds of an order.
 *
 * <p>The point of the service is that the money ACTUALLY LEAVES: the gateway
 * is called first, and the refund is recorded only when the gateway has
 * accepted it. A refund row without a gateway reference would be a promise
 * the store cannot keep.
 */
@ExtendWith(MockitoExtension.class)
class OrderRefundServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderRefundRepository refundRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentService paymentService;

    @org.mockito.Mock
    private com.nocteon.nocteon_api.audit.service.AuditLogService auditLogService;

    @InjectMocks
    private OrderRefundService orderRefundService;

    private CreateRefundRequest request(String amount) {
        CreateRefundRequest request = new CreateRefundRequest();
        request.setAmount(new BigDecimal(amount));
        request.setReason("The customer returned the bag");
        return request;
    }

    private Order paidOrder() {
        return Order.builder()
                .id(1L)
                .status(com.nocteon.nocteon_api.order.enums.OrderStatus.DELIVERED)
                .paymentStatus(PaymentStatus.PAID)
                .totalAmount(new BigDecimal("120.50"))
                .build();
    }

    private Payment paidPayment() {
        return Payment.builder()
                .id(9L)
                .amount(new BigDecimal("120.50"))
                .providerPaymentId("TXN-1")
                .build();
    }

    @Test
    void recordsTheAmountAndClosesThePayment() {
        Order order = paidOrder();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findFirstByOrderIdOrderByAttemptNumberDesc(1L))
                        .thenReturn(Optional.of(paidPayment()));
        when(refundRepository.findByOrderIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(paymentService.refund(any(), any())).thenReturn("REF-1");
        when(refundRepository.save(any(OrderRefund.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderRefundResponse response = orderRefundService.refund(1L, request("50.00"), "admin@aurum.local");

        assertThat(response.getAmount()).isEqualByComparingTo("50.00");
        assertThat(response.getReason()).isEqualTo("The customer returned the bag");
        assertThat(response.getCreatedBy()).isEqualTo("admin@aurum.local");
        // The gateway's own reference: the money really went back.
        assertThat(response.getGatewayReference()).isEqualTo("REF-1");
        assertThat(response.isManual()).isFalse();
        // And the order is now flagged as refunded.
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void aRefusedGatewayRecordsNothing() {
        Order order = paidOrder();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findFirstByOrderIdOrderByAttemptNumberDesc(1L))
                        .thenReturn(Optional.of(paidPayment()));
        when(refundRepository.findByOrderIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(paymentService.refund(any(), any()))
                        .thenThrow(new RefundFailedException("The gateway said no"));

        assertThatThrownBy(() -> orderRefundService.refund(1L, request("50.00"), "admin"))
                        .isInstanceOf(RefundFailedException.class);

        // NOTHING is recorded: the store never believes it gave the money
        // back when it did not.
        verify(refundRepository, never()).save(any());
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void refusesAnOrderThatWasNeverPaid() {
        Order order = Order.builder()
                        .id(1L)
                        .paymentStatus(PaymentStatus.PENDING)
                        .totalAmount(new BigDecimal("120.50"))
                        .build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderRefundService.refund(1L, request("50.00"), "admin"))
                        .isInstanceOf(InvalidRefundException.class);

        verify(refundRepository, never()).save(any());
    }

    @Test
    void refusesMoreThanWasPaid() {
        Order order = paidOrder();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findFirstByOrderIdOrderByAttemptNumberDesc(1L))
                        .thenReturn(Optional.of(paidPayment()));
        when(refundRepository.findByOrderIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> orderRefundService.refund(1L, request("500.00"), "admin"))
                        .isInstanceOf(InvalidRefundException.class);

        verify(refundRepository, never()).save(any());
    }

    @Test
    void refusesMoreThanIsLeftAfterAnEarlierRefund() {
        Order order = paidOrder();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findFirstByOrderIdOrderByAttemptNumberDesc(1L))
                        .thenReturn(Optional.of(paidPayment()));
        when(refundRepository.findByOrderIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(
                        OrderRefund.builder().amount(new BigDecimal("100.00")).build()));

        // Only 20.50 is left of the 120.50 that was paid.
        assertThatThrownBy(() -> orderRefundService.refund(1L, request("50.00"), "admin"))
                        .isInstanceOf(InvalidRefundException.class);
    }

    @Test
    void throwsWhenTheOrderDoesNotExist() {
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderRefundService.refund(1L, request("50.00"), "admin"))
                        .isInstanceOf(OrderNotFoundException.class);
    }
}
