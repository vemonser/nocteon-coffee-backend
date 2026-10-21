package com.nocteon.nocteon_api.mail.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.nocteon.nocteon_api.mail.config.MailProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Retries queued emails that a previous send failed to deliver.
 *
 * <p>Gated behind {@link MailProperties#isSchedulingEnabled()}, which defaults to false. This job
 * had never run — {@code @EnableScheduling} was missing from the project — and a 60-second loop that
 * calls out to Brevo is not something to switch on as a side effect of enabling scheduling for
 * something else. Until {@code MAIL_SCHEDULING_ENABLED=true} is set deliberately, this method
 * returns without draining the queue.
 *
 * <p>The gate is the first statement, so a disabled deployment does not read the pending-email
 * table at all.
 *
 * <h2>Why this method carries no {@code @Transactional}</h2>
 * Same reason as {@code AbandonedCartScheduler}: a {@code @Transactional} annotation here would be
 * opened by the proxy before the gate ran, so a disabled deployment would take a pooled connection
 * and commit an empty transaction on every one of its 60-second ticks. The transaction belongs on
 * {@link TransactionalEmailService#retryPendingEmails()}, which is only reached once the gate has
 * passed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailRetryScheduler {

    private final TransactionalEmailService emailService;
    private final MailProperties mailProperties;

    @Scheduled(fixedDelay = 60_000)
    public void run() {
        if (!mailProperties.isRetryEnabled()) {
            log.debug("Mail retry is disabled, skipping the email retry pass");
            return;
        }

        emailService.retryPendingEmails();
    }
}