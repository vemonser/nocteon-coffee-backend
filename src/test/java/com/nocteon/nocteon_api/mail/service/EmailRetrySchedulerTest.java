package com.nocteon.nocteon_api.mail.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nocteon.nocteon_api.mail.config.MailProperties;

/**
 * The retry worker has its OWN switch.
 *
 * <p>The existing scheduling-enabled flag also gates the abandoned-cart job,
 * and switching it on would start abandoned-cart emails to real addresses —
 * not a decision the reply-email feature gets to make.
 */
@ExtendWith(MockitoExtension.class)
class EmailRetrySchedulerTest {

    @Mock
    private TransactionalEmailService emailService;

    @Mock
    private MailProperties mailProperties;

    @InjectMocks
    private EmailRetryScheduler scheduler;

    @Test
    void aDisabledWorkerDoesNotTouchTheQueue() {
        when(mailProperties.isRetryEnabled()).thenReturn(false);

        scheduler.run();

        verify(emailService, never()).retryPendingEmails();
    }

    @Test
    void anEnabledWorkerDrainsTheQueue() {
        when(mailProperties.isRetryEnabled()).thenReturn(true);

        scheduler.run();

        verify(emailService).retryPendingEmails();
    }
}
