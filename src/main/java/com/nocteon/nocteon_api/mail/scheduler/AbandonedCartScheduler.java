package com.nocteon.nocteon_api.mail.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.nocteon.nocteon_api.cart.entity.Cart;
import com.nocteon.nocteon_api.cart.repository.CartRepository;
import com.nocteon.nocteon_api.mail.enums.EmailType;
import com.nocteon.nocteon_api.mail.service.TransactionalEmailService;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class AbandonedCartScheduler {

    private final CartRepository cartRepository;
    private final TransactionalEmailService transactionalEmailService;

    private static final Duration INACTIVITY_WINDOW = Duration.ofHours(2);
    private static final Duration REMINDER_COOLDOWN = Duration.ofDays(3);

    @Scheduled(cron = "0 0 * * * *") 
    @Transactional
    public void detectAndNotify() {
        Instant inactivityThreshold = Instant.now().minus(INACTIVITY_WINDOW);
        Instant reminderCooldown = Instant.now().minus(REMINDER_COOLDOWN);

        List<Cart> abandonedCarts = cartRepository.findAbandonedCarts(inactivityThreshold, reminderCooldown);

        log.info("Found {} abandoned carts to notify", abandonedCarts.size());

        for (Cart cart : abandonedCarts) {
            String idempotencyKey = "CART-" + cart.getId() + "-ABANDONED-" + LocalDate.now();

            transactionalEmailService.enqueueEmail(
                    idempotencyKey,
                    EmailType.ABANDONED_CART,
                    cart.getUser().getEmail(),
                    "CART",
                    cart.getId()
            );

            cart.setLastRemindedAt(Instant.now());
            cartRepository.save(cart);
        }
    }
}