package com.nocteon.nocteon_api.mail.listener;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.nocteon.nocteon_api.mail.enums.EmailType;
import com.nocteon.nocteon_api.mail.service.TransactionalEmailService;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.event.OrderShippedEvent;
import com.nocteon.nocteon_api.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderShippedEmailListener {

    private final TransactionalEmailService transactionalEmailService;
    private final OrderRepository orderRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderShipped(OrderShippedEvent event) {
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new IllegalStateException("Order not found: " + event.orderId()));

        transactionalEmailService.enqueueEmail(
                "ORDER-" + event.orderId() + "-SHIPPED",
                EmailType.ORDER_SHIPPED,
                order.getUser().getEmail(),
                "ORDER",
                event.orderId()
        );
    }
}