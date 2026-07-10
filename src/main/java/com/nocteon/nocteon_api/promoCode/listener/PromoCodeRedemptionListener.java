package com.nocteon.nocteon_api.promoCode.listener;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.nocteon.nocteon_api.mail.event.PaymentSucceededEvent;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.promoCode.entity.PromoCodeRedemption;
import com.nocteon.nocteon_api.promoCode.repository.PromoCodeRedemptionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PromoCodeRedemptionListener {

    private final OrderRepository orderRepository;
    private final PromoCodeRedemptionRepository redemptionRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new IllegalStateException("Order not found: " + event.orderId()));

        if (order.getPromoCode() == null) {
            return; 
        }

        boolean alreadyRecorded = redemptionRepository.existsByOrderId(order.getId());
        if (alreadyRecorded) {
            log.info("Redemption already recorded for order {}, skipping", order.getId());
            return;
        }

        PromoCodeRedemption redemption = PromoCodeRedemption.builder()
                .promoCode(order.getPromoCode())
                .user(order.getUser())
                .order(order)
                .discountAmount(order.getDiscountAmount())
                .build();

        redemptionRepository.save(redemption);
        log.info("Promo code redemption recorded for order {}", order.getId());
    }
}