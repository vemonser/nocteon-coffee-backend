package com.nocteon.nocteon_api.mail.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.cart.entity.Cart;
import com.nocteon.nocteon_api.cart.repository.CartRepository;
import com.nocteon.nocteon_api.mail.enums.EmailType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The working half of the abandoned-cart job: find the carts nobody came back to and enqueue one
 * reminder each, inside a single transaction.
 *
 * <h2>Why this is a separate bean</h2>
 * {@code AbandonedCartScheduler} checks {@link
 * com.nocteon.nocteon_api.mail.config.MailProperties#isSchedulingEnabled()} before it does anything
 * else, because a disabled deployment must not even read the carts table. That check cannot live
 * inside a method that is already {@code @Transactional}: the Spring proxy opens the transaction
 * <em>before</em> the first statement of the body runs, so the gate would sit inside a transaction
 * that has already taken a connection from the pool — and would be committed on the way out, empty
 * but paid for, on every scheduled tick.
 *
 * <p>So the gate stays on the scheduler, which holds no transaction at all, and the work moves
 * here, where {@link #notifyAbandonedCarts()} is a plain {@code @Transactional} method reached
 * through the proxy only once the gate has passed. This is the same shape
 * {@code EmailRetryScheduler} already had: an un-annotated {@code run()} that gates, then calls a
 * transactional collaborator.
 *
 * <p>Splitting the bean is what makes the ordering structural instead of incidental. A
 * self-injected or {@code @Lazy} reference to the scheduler itself would work, but it introduces a
 * circular dependency proxy for the sake of one annotation, and there is no precedent for that in
 * this codebase.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AbandonedCartNotificationService {

    private final CartRepository cartRepository;
    private final TransactionalEmailService transactionalEmailService;

    /** Carts untouched for this long are considered abandoned. */
    static final Duration INACTIVITY_WINDOW = Duration.ofHours(2);

    /** A cart already reminded within this window is not reminded again. */
    static final Duration REMINDER_COOLDOWN = Duration.ofDays(3);

    /**
     * Enqueues one reminder per abandoned cart and stamps the cart so the next pass skips it.
     *
     * <p>Not {@code readOnly}: the run stamps {@code lastRemindedAt} on every cart it notifies.
     *
     * @return how many carts were notified; zero on a clean pass
     */
    @Transactional
    public int notifyAbandonedCarts() {
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

        return abandonedCarts.size();
    }
}