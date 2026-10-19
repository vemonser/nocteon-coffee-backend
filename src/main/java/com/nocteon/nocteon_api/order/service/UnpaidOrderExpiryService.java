package com.nocteon.nocteon_api.order.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.entity.OrderItem;
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
            order.setStatus(OrderStatus.CANCELLED);
            order.setPaymentStatus(PaymentStatus.FAILED);

            for (OrderItem item : order.getItems()) {
                ProductVariant variant = item.getProductVariant();
                variant.setStockQuantity(variant.getStockQuantity() + item.getQuantity());
                variantRepository.save(variant);
            }

            paymentRepository.findByOrderId(order.getId()).ifPresent(payment -> {
                if (payment.getStatus() == PaymentStatus.PENDING) {
                    payment.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(payment);
                }
            });

            orderRepository.save(order);
            log.info("Released abandoned checkout: order {} ({} items) cancelled, stock restored",
                    order.getId(), order.getItems().size());
        }

        return expired.size();
    }
}
