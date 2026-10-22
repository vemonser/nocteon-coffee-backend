package com.nocteon.nocteon_api.mail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for outbound mail. Today it carries exactly one switch: whether the background mail
 * jobs are allowed to run.
 *
 * <p>Bound from {@code nocteon.mail.scheduling-enabled}, which defaults to {@code false} via
 * {@code ${MAIL_SCHEDULING_ENABLED:false}} in {@code application.yaml}, and is registered by
 * {@code SchedulingConfig}.
 *
 * <h2>Why the gate exists</h2>
 * This project had no {@code @EnableScheduling}, so {@code AbandonedCartScheduler} and
 * {@code EmailRetryScheduler} were written but had never run — neither had ever executed, so neither
 * has a track record. Enabling scheduling (needed for the AI nightly reindex) also enabled them,
 * which would have started sending mail and calling Brevo on a deploy that had nothing to do with
 * mail. The flag is what lets scheduling be switched on without that happening: set it to
 * {@code true} deliberately, per environment, once somebody has decided these jobs should send.
 *
 * <p>Default off is the only defensible direction. Sending abandoned-cart reminders to real
 * addresses, and retrying a queue nobody has inspected, are not reversible by a config reload — the
 * emails are already delivered.
 *
 * <p>The getter is Lombok-generated for a {@code boolean} field, so it reads
 * {@code isSchedulingEnabled()}, not {@code getSchedulingEnabled()}.
 *
 * <p>Deliberately not on the class: the other keys under {@code nocteon.mail} in
 * {@code application.yaml} are not read from Java, so this class is not the one place to look for
 * every mail setting — it is the gate.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "nocteon.mail")
public class MailProperties {

    /**
     * Whether {@code AbandonedCartScheduler} and {@code EmailRetryScheduler} may run. {@code false}
     * leaves both beans in place but inert: each scheduled method returns before touching the
     * database or the mail provider.
     */
    private boolean schedulingEnabled;

    /**
     * Whether the email RETRY worker may run.
     *
     * <p>A separate switch from {@link #isSchedulingEnabled()}, on purpose.
     * That flag also gates {@code AbandonedCartScheduler}, and switching it
     * on would start abandoned-cart reminders to real addresses — not a
     * decision the reply-email feature gets to make. This flag turns on the
     * retry of FAILED transactional emails only.
     */
    private boolean retryEnabled;

    /**
     * Hours of inactivity before a cart is considered abandoned. Default 24h.
     * Used by both the dashboard status calculation and the abandoned-cart mail job.
     */
    private int abandonedCartHours = 24;
}