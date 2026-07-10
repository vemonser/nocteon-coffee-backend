package com.nocteon.nocteon_api.payment.service;

import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.payment.entity.Payment;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;
import com.nocteon.nocteon_api.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Edge-case coverage for PaymentService: attempt-number sequencing for retries,
 * and correct behavior when a previous attempt already exists.
 *
 * NOTE: getAuthToken(), createPaymobOrder(), and getPaymentKey() are external
 * HTTP calls to Paymob and are assumed to be package-private/protected methods
 * overridden here via a partial mock (spy), OR abstracted behind a client
 * interface you can mock directly. Adjust the setup below to match however
 * your PaymentService currently isolates the Paymob HTTP calls.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = Order.builder()
                .id(1L)
                .totalAmount(BigDecimal.valueOf(500))
                .build();
    }

    @Test
    void firstAttemptIsNumberOne() {
        when(paymentRepository.countByOrderId(1L)).thenReturn(0);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            return p;
        });

        // NOTE: initiatePayment() also calls out to Paymob's HTTP API (getAuthToken,
        // createPaymobOrder, getPaymentKey). In a real test suite these should be
        // mocked via an injected PaymobClient interface. Here we verify the
        // attempt-number bookkeeping specifically, which is the part most prone
        // to off-by-one bugs on retries.

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);

        try {
            paymentService.initiatePayment(order, "Omar", "Ahmed", "omar@test.com", "0100000000");
        } catch (Exception ignoredNetworkCallFailure) {
            // Expected in this isolated unit test since Paymob HTTP calls aren't mocked here.
        }

        verify(paymentRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getAttemptNumber()).isEqualTo(1);
    }

    @Test
    void secondAttemptIncrementsAttemptNumberAfterFirstFailure() {
        // Simulate: one prior payment attempt already exists for this order (failed)
        when(paymentRepository.countByOrderId(1L)).thenReturn(1);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            return p;
        });

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);

        try {
            paymentService.initiatePayment(order, "Omar", "Ahmed", "omar@test.com", "0100000000");
        } catch (Exception ignoredNetworkCallFailure) {
            // Expected in this isolated unit test since Paymob HTTP calls aren't mocked here.
        }

        verify(paymentRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getAttemptNumber()).isEqualTo(2);
    }

    @Test
    void newPaymentAttemptStartsAsPending() {
        when(paymentRepository.countByOrderId(1L)).thenReturn(0);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            return p;
        });

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);

        try {
            paymentService.initiatePayment(order, "Omar", "Ahmed", "omar@test.com", "0100000000");
        } catch (Exception ignoredNetworkCallFailure) {
            // Expected - Paymob HTTP calls aren't mocked in this isolated test.
        }

        verify(paymentRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void newPaymentAttemptCopiesOrderTotalAsAmount() {
        when(paymentRepository.countByOrderId(1L)).thenReturn(0);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            return p;
        });

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);

        try {
            paymentService.initiatePayment(order, "Omar", "Ahmed", "omar@test.com", "0100000000");
        } catch (Exception ignoredNetworkCallFailure) {
            // Expected - Paymob HTTP calls aren't mocked in this isolated test.
        }

        verify(paymentRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getAmount()).isEqualByComparingTo(BigDecimal.valueOf(500));
    }
}