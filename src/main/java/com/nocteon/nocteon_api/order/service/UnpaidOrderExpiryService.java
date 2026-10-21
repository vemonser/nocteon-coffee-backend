package com.nocteon.nocteon_api.order.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.entity.OrderItem;
import com.nocteon.nocteon_api.order.dto.request.UpdateOrderStatusRequest;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;
import com.nocteon.nocteon_api.payment.repository.PaymentRepository;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Releases stock held by checkouts nobody is paying for.
 *
 * <p>An online order reserves its units the moment it is created, before the
 * buyer pays. If the buyer closes the tab, the payment callback never arrives
 * and — without this sweep — the units stay reserved forever while the next
 * customer sees "out of stock".
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnpaidOrderExpiryService {

    private final OrderRepository orderRepository;
    private final OrderStatusService orderStatusService;
    private final ProductVariantRepository variantRepository;
    private final PaymentRepository paymentRepository;

    @Value("${application.orders.unpaid-timeout-minutes:30}")
    private long unpaidTimeoutMinutes;

    /**
     * Cancels every order still awaiting payment past the timeout and gives
     * its units back to the shelf. Mirrors a failed payment: the order is
     * CANCELLED, its payment is FAILED, and the stock comes back exactly once.
     *
     * @return how many abandoned checkouts were released
     */
    @Transactional
    public int cancelExpiredUnpaidOrders() {
        Instant cutoff = Instant.now().minus(unpaidTimeoutMinutes, ChronoUnit.MINUTES);
        List<Order> expired = orderRepository
                .findByStatusAndPaymentStatusAndCreatedAtBefore(
                        OrderStatus.PENDING, PaymentStatus.PENDING, cutoff);

        for (Order order : expired) {
            // The status change goes through the state machine, so the
            // history records WHO cancelled it and WHY: the sweep is a status
            // change like any other, and the order page shows it.
            UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
            request.setStatus(OrderStatus.CANCELLED);
            request.setReason("The payment was not received in time.");

            order.setPaymentStatus(PaymentStatus.FAILED);

            // The stock is NOT released here: the state machine releases it
            // when it cancels the order, and releasing it in both places
            // would put the units back twice.

            paymentRepository.findByOrderId(order.getId()).ifPresent(payment -> {
                if (payment.getStatus() == PaymentStatus.PENDING) {
                    payment.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(payment);
                }
            });

            orderRepository.save(order);
            orderStatusService.updateStatus(order.getId(), request, "system:expiry-sweep");
            log.info("Released abandoned checkout: order {} ({} items) cancelled, stock restored",
                    order.getId(), order.getItems().size());
        }

        return expired.size();
    }
}
