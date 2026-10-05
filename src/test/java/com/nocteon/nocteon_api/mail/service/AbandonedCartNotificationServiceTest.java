package com.nocteon.nocteon_api.mail.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.cart.entity.Cart;
import com.nocteon.nocteon_api.cart.repository.CartRepository;
import com.nocteon.nocteon_api.mail.enums.EmailType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the transactional half of the abandoned-cart job, which moved out of the scheduler so the
 * gate could run outside a transaction. The scheduler's own gate behaviour is asserted in
 * {@code AbandonedCartSchedulerTest}.
 */
@ExtendWith(MockitoExtension.class)
class AbandonedCartNotificationServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private TransactionalEmailService transactionalEmailService;

    @InjectMocks
    private AbandonedCartNotificationService service;

    private static Cart abandonedCart(long id, String email) {
        return Cart.builder()
                .id(id)
                .user(User.builder().email(email).build())
                .build();
    }

    @Test
    void anEnabledRunQueuesOneReminderPerAbandonedCart() {
        when(cartRepository.findAbandonedCarts(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(abandonedCart(5L, "shopper@example.com")));

        int notified = service.notifyAbandonedCarts();

        assertThat(notified).isEqualTo(1);
        verify(transactionalEmailService).enqueueEmail(
                contains("CART-5-ABANDONED"), eq(EmailType.ABANDONED_CART),
                eq("shopper@example.com"), eq("CART"), eq(5L));
        verify(cartRepository).save(any(Cart.class));
    }

    /** The gate must not turn into a silent hole once enabled: no carts still means a clean run. */
    @Test
    void anEnabledRunWithNothingAbandonedSendsNothing() {
        when(cartRepository.findAbandonedCarts(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        assertThat(service.notifyAbandonedCarts()).isZero();

        verify(transactionalEmailService, never()).enqueueEmail(
                any(), any(), any(), any(), any());
    }

    /**
     * The lookup is a pair of windows, not a boolean: carts idle past two hours, and not reminded
     * within three days. These are what the gate is protecting, so they are pinned here rather than
     * left as two magic durations in the query call.
     */
    @Test
    void theQueryAsksForTwoHoursOfInactivityAndAThreeDayCooldown() {
        when(cartRepository.findAbandonedCarts(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        Instant before = Instant.now();
        service.notifyAbandonedCarts();

        ArgumentCaptor<Instant> inactivity = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> cooldown = ArgumentCaptor.forClass(Instant.class);
        verify(cartRepository).findAbandonedCarts(inactivity.capture(), cooldown.capture());

        assertThat(inactivity.getValue())
                .isBetween(before.minus(Duration.ofHours(2)), before);
        assertThat(cooldown.getValue())
                .isBetween(before.minus(Duration.ofDays(3)), before);
    }

    /**
     * The cooldown is enforced by stamping the cart, not by trusting the query alone, so the stamp
     * has to survive the run or the next hourly pass would mail the same shopper again.
     */
    @Test
    void aNotifiedCartIsStampedForTheNextPass() {
        Cart cart = abandonedCart(9L, "shopper@example.com");
        when(cartRepository.findAbandonedCarts(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(cart));

        Instant before = Instant.now();
        service.notifyAbandonedCarts();

        ArgumentCaptor<Cart> saved = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository).save(saved.capture());

        assertThat(saved.getValue().getLastRemindedAt()).isBetween(before, Instant.now().plusSeconds(1));
    }
}