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
import com.nocteon.nocteon_api.payment.repository.PaymentRepository;

/**
 * The refunds of an order.
 *
 * <p>The point of the service is the AMOUNT: without it, the net paid of an
 * order cannot be computed. And the point of the limits is that a refund can
 * never be bigger than what was paid.
 */
@ExtendWith(MockitoExtension.class)
class OrderRefundServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderRefundRepository refundRepository;
    @Mock private PaymentRepository paymentRepository;

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

    @Test
    void recordsTheAmountAndClosesThePayment() {
        Order order = paidOrder();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findFirstByOrderIdOrderByAttemptNumberDesc(1L))
                        .thenReturn(Optional.of(Payment.builder().amount(new BigDecimal("120.50")).build()));
        when(refundRepository.findByOrderIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(refundRepository.save(any(OrderRefund.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderRefundResponse response = orderRefundService.refund(1L, request("50.00"), "admin@aurum.local");

        assertThat(response.getAmount()).isEqualByComparingTo("50.00");
        assertThat(response.getReason()).isEqualTo("The customer returned the bag");
        assertThat(response.getCreatedBy()).isEqualTo("admin@aurum.local");
        // The money has NOT gone through the gateway: the row says so.
        assertThat(response.isManual()).isTrue();
        // And the order is now flagged as refunded.
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
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
                        .thenReturn(Optional.of(Payment.builder().amount(new BigDecimal("120.50")).build()));
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
                        .thenReturn(Optional.of(Payment.builder().amount(new BigDecimal("120.50")).build()));
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
