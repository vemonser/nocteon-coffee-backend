package com.nocteon.nocteon_api.mail.scheduler;

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
import com.nocteon.nocteon_api.mail.config.MailProperties;
import com.nocteon.nocteon_api.mail.enums.EmailType;
import com.nocteon.nocteon_api.mail.service.TransactionalEmailService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the scheduling gate on the abandoned-cart job.
 *
 * <p>{@code @EnableScheduling} was missing from this project, so this job — and the email retry
 * loop next to it — had never run. Turning scheduling on made both live, and a job that has never
 * executed is exactly the kind of thing that starts sending mail the moment it is switched on. The
 * gate therefore defaults to off: nothing here runs until somebody sets
 * {@code MAIL_SCHEDULING_ENABLED=true} on purpose.
 *
 * <p>The two assertions that matter are therefore the gate ones: with the flag off, the scheduled
 * method must not reach the repository or the mail service at all — not "should send nothing", but
 * "should do nothing".
 */
@ExtendWith(MockitoExtension.class)
class AbandonedCartSchedulerTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private TransactionalEmailService transactionalEmailService;

    @Mock
    private MailProperties mailProperties;

    @InjectMocks
    private AbandonedCartScheduler scheduler;

    private static Cart abandonedCart(long id, String email) {
        return Cart.builder()
                .id(id)
                .user(User.builder().email(email).build())
                .build();
    }

    /**
     * The gate is checked before the query, so a disabled deployment does not even read the carts
     * table. A guard placed after the lookup would still find every abandoned cart and only then
     * decide not to mail anybody.
     */
    @Test
    void detectAndNotifyIsANoOpWhenMailSchedulingIsDisabled() {
        when(mailProperties.isSchedulingEnabled()).thenReturn(false);

        scheduler.detectAndNotify();

        verifyNoInteractions(cartRepository, transactionalEmailService);
    }

    @Test
    void detectAndNotifyRunsWhenMailSchedulingIsEnabled() {
        when(mailProperties.isSchedulingEnabled()).thenReturn(true);
        when(cartRepository.findAbandonedCarts(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(abandonedCart(5L, "shopper@example.com")));

        scheduler.detectAndNotify();

        verify(cartRepository).findAbandonedCarts(any(Instant.class), any(Instant.class));
        verify(transactionalEmailService).enqueueEmail(
                contains("CART-5-ABANDONED"), eq(EmailType.ABANDONED_CART),
                eq("shopper@example.com"), eq("CART"), eq(5L));
        verify(cartRepository).save(any(Cart.class));
    }

    /** The gate must not turn into a silent hole once enabled: no carts still means a clean run. */
    @Test
    void anEnabledRunWithNothingAbandonedSendsNothing() {
        when(mailProperties.isSchedulingEnabled()).thenReturn(true);
        when(cartRepository.findAbandonedCarts(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        scheduler.detectAndNotify();

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
        when(mailProperties.isSchedulingEnabled()).thenReturn(true);
        when(cartRepository.findAbandonedCarts(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        Instant before = Instant.now();
        scheduler.detectAndNotify();

        ArgumentCaptor<Instant> inactivity = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> cooldown = ArgumentCaptor.forClass(Instant.class);
        verify(cartRepository).findAbandonedCarts(inactivity.capture(), cooldown.capture());

        assertThat(inactivity.getValue())
                .isBetween(before.minus(Duration.ofHours(2)), before);
        assertThat(cooldown.getValue())
                .isBetween(before.minus(Duration.ofDays(3)), before);
    }
}