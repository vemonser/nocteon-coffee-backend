package com.nocteon.nocteon_api.mail.config;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the shipped default and the exact environment variable that flips the gate.
 *
 * <p>Everything the gate protects depends on this one binding, and both failure modes are silent:
 * a typo in {@code application.yaml} would leave the jobs dormant forever while an operator
 * believed they were enabled, and a missing {@code :false} fallback would switch them on for
 * everybody. Neither is visible from a unit test of the scheduler, because the scheduler only ever
 * sees a {@link MailProperties} someone handed it — so this test binds the real configuration
 * file instead of asserting on the class.
 *
 * <p>No Spring Boot auto-configuration is loaded: {@link ApplicationContextRunner} builds a plain
 * context around {@link MailProperties} alone, which is why it can run without the database and
 * the {@code vector} extension that {@code contextLoads} needs.
 */
class MailPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(BindingOnly.class);

    /** Just the properties registration, so nothing else in the application is pulled in. */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MailProperties.class)
    static class BindingOnly {
    }

    @Test
    void theGateIsClosedWithTheShippedConfiguration() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(MailProperties.class).isSchedulingEnabled()).isFalse();
        });
    }

    /**
     * The name has to match {@code ${MAIL_SCHEDULING_ENABLED:false}} exactly, which is the whole
     * point of the variable: an operator sets it in the environment, and a mismatch here means the
     * jobs stay dormant no matter what they do.
     */
    @Test
    void theEnvironmentVariableOpensTheGate() {
        runner.withPropertyValues("MAIL_SCHEDULING_ENABLED=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(MailProperties.class).isSchedulingEnabled()).isTrue();
        });
    }
}