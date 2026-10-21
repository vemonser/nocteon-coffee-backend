package com.nocteon.nocteon_api.mail.listener;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.nocteon.nocteon_api.mail.enums.EmailType;
import com.nocteon.nocteon_api.mail.service.TransactionalEmailService;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.event.OrderStatusChangedEvent;
import com.nocteon.nocteon_api.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tells the customer that the status of their order changed.
 *
 * <p>Only fires when the admin ticked "notify the customer": the admin
 * decides who hears about it.
 *
 * <p>The e-mail is enqueued AFTER the transaction commits, so a customer is
 * never told about a change that was rolled back.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderStatusChangedEmailListener {

    private final TransactionalEmailService transactionalEmailService;
    private final OrderRepository orderRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        Order order = orderRepository.findById(event.orderId())
                        .orElseThrow(() -> new IllegalStateException("Order not found: " + event.orderId()));

        if (order.getUser() == null || order.getUser().getEmail() == null) {
                log.warn("Order {} has no customer to notify about its status change", event.orderId());
                return;
        }

        transactionalEmailService.enqueueEmail(
                        "ORDER-" + event.orderId() + "-STATUS-" + event.toStatus().name(),
                        EmailType.ORDER_STATUS_CHANGED,
                        order.getUser().getEmail(),
                        "ORDER",
                        event.orderId());
    }
}
