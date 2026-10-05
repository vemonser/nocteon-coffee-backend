package com.nocteon.nocteon_api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns {@code @Scheduled} on.
 *
 * <p>Spring Boot does <em>not</em> enable scheduling by itself: without this annotation nothing
 * registers {@code ScheduledAnnotationBeanPostProcessor}, and every {@code @Scheduled} method in the
 * application is silently inert. Spring Boot's scheduling auto-configuration only supplies the
 * task <em>scheduler</em> to run the tasks on; the wiring that discovers them is opt-in.
 *
 * <p>Three jobs depend on it, all of which predate this class and none of which was running until
 * now: {@code AbandonedCartScheduler} (hourly), {@code EmailRetryScheduler} (every 60s) and
 * {@code ProductIndexScheduler} (03:00 daily). Enabling this makes all three live at once — the
 * annotation is context-wide and cannot be scoped to the AI job — which is the intended behaviour
 * for all of them, but is worth naming plainly: the two mail jobs start sending on the next deploy.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}