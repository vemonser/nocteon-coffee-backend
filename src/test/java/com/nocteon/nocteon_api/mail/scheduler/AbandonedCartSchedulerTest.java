package com.nocteon.nocteon_api.mail.scheduler;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.mail.config.MailProperties;
import com.nocteon.nocteon_api.mail.service.AbandonedCartNotificationService;

import static org.assertj.core.api.Assertions.assertThat;
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
 * <p>The assertion that matters is therefore the gate one: with the flag off, the scheduled method
 * must not reach the transactional worker at all — not "should send nothing", but "should do
 * nothing". The work itself is asserted in
 * {@code AbandonedCartNotificationServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class AbandonedCartSchedulerTest {

    @Mock
    private AbandonedCartNotificationService notificationService;

    @Mock
    private MailProperties mailProperties;

    @InjectMocks
    private AbandonedCartScheduler scheduler;

    /**
     * The gate is checked before anything else, so a disabled deployment does not even reach the
     * query inside the transactional worker.
     */
    @Test
    void detectAndNotifyIsANoOpWhenMailSchedulingIsDisabled() {
        when(mailProperties.isSchedulingEnabled()).thenReturn(false);

        scheduler.detectAndNotify();

        verifyNoInteractions(notificationService);
    }

    @Test
    void detectAndNotifyRunsWhenMailSchedulingIsEnabled() {
        when(mailProperties.isSchedulingEnabled()).thenReturn(true);

        scheduler.detectAndNotify();

        verify(notificationService).notifyAbandonedCarts();
    }

    /**
     * The regression this class exists for.
     *
     * <p>{@code @Transactional} on the scheduled method would be opened by the proxy <em>before</em>
     * the gate ran, so every disabled tick would still take a pooled connection and commit an empty
     * transaction — the opposite of the documented "must not even read the carts table". The
     * annotation has to be absent here and present on the worker instead, which is what the two
     * assertions below pin.
     */
    @Test
    void theScheduledMethodHoldsNoTransaction() throws NoSuchMethodException {
        Method scheduled = AbandonedCartScheduler.class.getMethod("detectAndNotify");

        assertThat(scheduled.getAnnotation(Transactional.class))
                .as("detectAndNotify() must not be @Transactional — the gate runs before any transaction opens")
                .isNull();
    }

    @Test
    void theTransactionalWorkerIsAnnotated() throws NoSuchMethodException {
        Method worker = AbandonedCartNotificationService.class.getMethod("notifyAbandonedCarts");

        assertThat(worker.getAnnotation(Transactional.class))
                .as("the work must still run in one transaction")
                .isNotNull();
    }
}