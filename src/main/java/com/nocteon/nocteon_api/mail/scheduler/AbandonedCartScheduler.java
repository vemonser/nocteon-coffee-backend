package com.nocteon.nocteon_api.mail.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.nocteon.nocteon_api.cart.entity.Cart;
import com.nocteon.nocteon_api.cart.repository.CartRepository;
import com.nocteon.nocteon_api.mail.config.MailProperties;
import com.nocteon.nocteon_api.mail.enums.EmailType;
import com.nocteon.nocteon_api.mail.service.TransactionalEmailService;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Finds carts nobody came back to and enqueues a reminder for each.
 *
 * <p>Gated behind {@link MailProperties#isSchedulingEnabled()}, which defaults to false. This job
 * had never run — {@code @EnableScheduling} was missing from the project — and a job that has never
 * executed cannot be assumed correct, so it stays dormant until somebody sets
 * {@code MAIL_SCHEDULING_ENABLED=true} on purpose. Sending real reminders to real addresses is not
 * something a deploy should do on its own initiative.
 *
 * <p>The gate is the first statement, before the query: a disabled deployment must not even read
 * the carts table, and must certainly not write to it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AbandonedCartScheduler {

    private final CartRepository cartRepository;
    private final TransactionalEmailService transactionalEmailService;
    private final MailProperties mailProperties;

    private static final Duration INACTIVITY_WINDOW = Duration.ofHours(2);
    private static final Duration REMINDER_COOLDOWN = Duration.ofDays(3);

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void detectAndNotify() {
        if (!mailProperties.isSchedulingEnabled()) {
            log.debug("Mail scheduling is disabled, skipping the abandoned-cart scan");
            return;
        }

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