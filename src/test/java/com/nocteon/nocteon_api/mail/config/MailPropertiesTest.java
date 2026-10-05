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

    /**
     * The shipped value, forced.
     *
     * <p>{@code application.yaml} writes {@code scheduling-enabled: ${MAIL_SCHEDULING_ENABLED:false}},
     * and that placeholder resolves against the real environment. An exported
     * {@code MAIL_SCHEDULING_ENABLED=true} — or a {@code .env} file, which
     * {@code spring-dotenv} imports — would otherwise decide the outcome of a test whose entire
     * claim is that the <em>shipped</em> default is closed. Setting the property explicitly here
     * makes the test depend on {@code application.yaml} alone: it still fails if the
     * {@code :false} fallback is deleted or flipped, and it no longer fails because of a shell the
     * build happened to run in.
     */
    private static final String SHIPPED_DEFAULT = "nocteon.mail.scheduling-enabled=false";

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
        runner.withPropertyValues(SHIPPED_DEFAULT).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(MailProperties.class).isSchedulingEnabled()).isFalse();
        });
    }

    /**
     * The point of the closed-gate test, isolated from the environment: the placeholder itself
     * carries {@code :false}, so an unset variable binds {@code false} rather than failing.
     *
     * <p>Asserted on the raw placeholder rather than through a context runner, because a runner
     * cannot distinguish "the default resolved to false" from "the environment said false" — which
     * is exactly the distinction this file needs to make.
     */
    @Test
    void theShippedPlaceholderFallsBackToFalse() {
        String yaml = readClasspathResource("application.yaml");

        assertThat(yaml)
                .as("nocteon.mail.scheduling-enabled must resolve to false when MAIL_SCHEDULING_ENABLED is unset")
                .contains("scheduling-enabled: ${MAIL_SCHEDULING_ENABLED:false}");
    }

    private static String readClasspathResource(String name) {
        try (java.io.InputStream stream = MailPropertiesTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(stream).as("%s must be on the test classpath", name).isNotNull();
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new AssertionError("Unable to read " + name + " from the classpath", e);
        }
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

    /**
     * Hermeticity in the other direction, and the reason the closed-gate test shadows the property
     * rather than reading it: the explicit value has to win over whatever the environment holds.
     *
     * <p>With {@code MAIL_SCHEDULING_ENABLED=false} forced and the variable also set to
     * {@code true}, the gate stays shut. Were the binding resolving from the OS environment instead
     * of the shadowed property, this would come back open — and
     * {@code theGateIsClosedWithTheShippedConfiguration} would then be at the mercy of the machine
     * running the build.
     */
    @Test
    void theShadowedDefaultBeatsTheEnvironmentVariable() {
        runner.withPropertyValues(SHIPPED_DEFAULT, "MAIL_SCHEDULING_ENABLED=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(MailProperties.class).isSchedulingEnabled()).isFalse();
        });
    }
}