package com.nocteon.nocteon_api.mail.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nocteon.nocteon_api.mail.config.MailProperties;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the scheduling gate on the email retry loop.
 *
 * <p>This job runs every 60 seconds and calls out to Brevo. It had never run, because
 * {@code @EnableScheduling} was absent from the project; enabling scheduling for the AI nightly
 * reindex also switched it on. It is therefore behind the same default-off gate as
 * {@code AbandonedCartScheduler}: a retry loop against a mail provider is not something a deploy
 * should start sending on its own, and nobody can tell from the outside whether the queue it would
 * drain is even populated.
 */
@ExtendWith(MockitoExtension.class)
class EmailRetrySchedulerTest {

    @Mock
    private TransactionalEmailService emailService;

    @Mock
    private MailProperties mailProperties;

    @InjectMocks
    private EmailRetryScheduler scheduler;

    /**
     * Not "sends nothing" but "does nothing": a disabled deployment must not drain the queue or
     * touch the provider, which is what a caller-visible no-op with no collaborators proves.
     */
    @Test
    void runIsANoOpWhenMailSchedulingIsDisabled() {
        when(mailProperties.isSchedulingEnabled()).thenReturn(false);

        scheduler.run();

        verifyNoInteractions(emailService);
    }

    @Test
    void runRetriesPendingEmailsWhenMailSchedulingIsEnabled() {
        when(mailProperties.isSchedulingEnabled()).thenReturn(true);

        scheduler.run();

        verify(emailService).retryPendingEmails();
    }
}