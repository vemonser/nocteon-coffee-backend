package com.nocteon.nocteon_api.mail.listener;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.nocteon.nocteon_api.mail.enums.EmailType;
import com.nocteon.nocteon_api.mail.event.OrderPlacedEvent;
import com.nocteon.nocteon_api.mail.event.PaymentSucceededEvent;
import com.nocteon.nocteon_api.mail.service.TransactionalEmailService;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEmailEventListener {

        private final TransactionalEmailService  emailService;
        private final OrderRepository orderRepository;

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void onOrderPlaced(OrderPlacedEvent event) {
                Order order = orderRepository.findById(event.orderId())
                                .orElseThrow(() -> new IllegalStateException("Order not found: " + event.orderId()));

                emailService.enqueueEmail(
                                "ORDER-" + event.orderId() + "-CONFIRMATION",
                                EmailType.ORDER_CONFIRMATION,
                                order.getUser().getEmail(),
                                "ORDER",
                                event.orderId());
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void onPaymentSucceeded(PaymentSucceededEvent event) {
                Order order = orderRepository.findById(event.orderId())
                                .orElseThrow(() -> new IllegalStateException("Order not found: " + event.orderId()));

                emailService.enqueueEmail(
                                "PAYMENT-" + event.paymentId() + "-SUCCESS",
                                EmailType.PAYMENT_SUCCESS,
                                order.getUser().getEmail(),
                                "ORDER",
                                event.orderId());
        }
}