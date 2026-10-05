package com.nocteon.nocteon_api.mail.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.nocteon.nocteon_api.mail.config.MailProperties;
import com.nocteon.nocteon_api.mail.service.AbandonedCartNotificationService;

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
 *
 * <h2>Why this method carries no {@code @Transactional}</h2>
 * A {@code @Transactional} annotation here would be opened by the proxy <em>before</em> the gate ran,
 * so a disabled deployment would still take a connection from the pool and commit an empty
 * transaction on every tick — the opposite of "must not even read the carts table". The work
 * therefore lives in {@link AbandonedCartNotificationService#notifyAbandonedCarts()}, whose
 * transaction starts only after the gate has passed. This mirrors
 * {@link com.nocteon.nocteon_api.mail.service.EmailRetryScheduler}, which gates in an un-annotated
 * {@code run()} and delegates to a transactional collaborator.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AbandonedCartScheduler {

    private final MailProperties mailProperties;
    private final AbandonedCartNotificationService notificationService;

    @Scheduled(cron = "0 0 * * * *")
    public void detectAndNotify() {
        if (!mailProperties.isSchedulingEnabled()) {
            log.debug("Mail scheduling is disabled, skipping the abandoned-cart scan");
            return;
        }

        int notified = notificationService.notifyAbandonedCarts();

        log.info("Abandoned-cart pass finished, notified={}", notified);
    }
}