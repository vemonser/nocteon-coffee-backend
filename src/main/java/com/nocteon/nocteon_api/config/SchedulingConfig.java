package com.nocteon.nocteon_api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.nocteon.nocteon_api.mail.config.MailProperties;

/**
 * Turns {@code @Scheduled} on, and registers the gate the background mail jobs consult.
 *
 * <p>Spring Boot does <em>not</em> enable scheduling by itself: without {@code @EnableScheduling}
 * nothing registers {@code ScheduledAnnotationBeanPostProcessor}, and every {@code @Scheduled} method
 * in the application is silently inert. Spring Boot's scheduling auto-configuration only supplies the
 * task <em>scheduler</em> to run tasks on; the wiring that discovers them is opt-in.
 *
 * <h2>The three jobs, and which of them are live</h2>
 * <ul>
 * <li>{@code ProductIndexScheduler} — 03:00 daily, re-embeds the AI product index. Live. It is the
 * reason this class exists.</li>
 * <li>{@code AbandonedCartScheduler} — hourly, enqueues abandoned-cart reminder emails.</li>
 * <li>{@code EmailRetryScheduler} — every 60s, retries failed Brevo sends.</li>
 * </ul>
 *
 * <p>The last two predate this class and had never run: they were written without
 * {@code @EnableScheduling}, so nothing ever invoked them. Enabling scheduling therefore switched
 * them on too, and {@code @EnableScheduling} is context-wide — it cannot be narrowed to the AI job,
 * so there is no way to bring the nightly reindex online without also making the two mail jobs live.
 *
 * <p>That is why {@link MailProperties} is registered here. Both mail jobs return early while
 * {@code nocteon.mail.scheduling-enabled} is {@code false} (the default), which is the state this
 * class was introduced in: outbound mail stays exactly as it was before, and switching it on is a
 * deliberate, per-environment decision rather than a side effect of turning on scheduling.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(MailProperties.class)
public class SchedulingConfig {
}