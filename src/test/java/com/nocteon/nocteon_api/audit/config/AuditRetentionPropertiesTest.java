package com.nocteon.nocteon_api.audit.config;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the shipped defaults and the exact environment variables that turn the
 * audit sweep on.
 *
 * <p>Both failure modes of this configuration are silent. A typo in
 * {@code application.yaml} would leave the job dormant forever while an operator
 * believed history was being pruned; a missing {@code :false} fallback would
 * start deleting audit rows on every deployment. Neither shows up in a test of
 * the scheduler, which only ever sees the properties object it is handed — so
 * this test binds the real configuration file.
 *
 * <p>No auto-configuration is loaded: {@link ApplicationContextRunner} builds a
 * context around {@link AuditRetentionProperties} alone, which is why it runs
 * without the database and the {@code vector} extension {@code contextLoads}
 * needs.
 */
class AuditRetentionPropertiesTest {

    /**
     * The shipped value, forced.
     *
     * <p>{@code application.yaml} writes {@code enabled: ${AUDIT_RETENTION_ENABLED:false}},
     * and that placeholder resolves against the real environment. An exported
     * {@code AUDIT_RETENTION_ENABLED=true} — or a {@code .env} file, which
     * {@code spring-dotenv} imports — would otherwise decide the outcome of a test
     * whose whole claim is that the <em>shipped</em> default is closed. Shadowing
     * it here keeps the test dependent on {@code application.yaml} alone: it still
     * fails if the {@code :false} fallback is deleted or flipped, and it no longer
     * fails because of the shell the build happened to run in.
     */
    private static final String SHIPPED_DEFAULT = "app.audit.retention.enabled=false";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(BindingOnly.class);

    /** Just the properties registration, so nothing else in the application is pulled in. */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuditRetentionProperties.class)
    static class BindingOnly {
    }

    @Test
    void theGateIsClosedWithTheShippedConfiguration() {
        runner.withPropertyValues(SHIPPED_DEFAULT).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AuditRetentionProperties.class).isEnabled()).isFalse();
        });
    }

    /**
     * The class defaults must match the file, because {@code days} and
     * {@code batch-size} are read from the properties object and the file is
     * merely where they are documented.
     */
    @Test
    void theClassDefaultsAreSane() {
        AuditRetentionProperties defaults = new AuditRetentionProperties();

        assertThat(defaults.isEnabled()).isFalse();
        assertThat(defaults.getDays()).isEqualTo(180);
        assertThat(defaults.getBatchSize()).isEqualTo(5000);
    }

    /**
     * Asserted on the raw placeholders rather than through a context runner,
     * because a runner cannot tell "the default resolved to false" from "the
     * environment said false" — which is exactly the distinction this file needs
     * to make.
     */
    @Test
    void theShippedPlaceholdersCarryTheDocumentedDefaults() {
        String yaml = readClasspathResource("application.yaml");

        assertThat(yaml)
                .as("retention must stay off unless AUDIT_RETENTION_ENABLED is set")
                .contains("enabled: ${AUDIT_RETENTION_ENABLED:false}");
        assertThat(yaml)
                .as("the silent window must not shrink to nothing if the variable is unset")
                .contains("days: ${AUDIT_RETENTION_DAYS:180}");
        assertThat(yaml)
                .as("each delete statement must stay bounded if the variable is unset")
                .contains("batch-size: ${AUDIT_RETENTION_BATCH_SIZE:5000}");
    }

    /**
     * The names have to match exactly: an operator sets these in the
     * environment, and a mismatch means the sweep stays dormant (or runs with an
     * unexpected window) no matter what they do.
     */
    @Test
    void theEnvironmentVariablesOpenTheGateAndSetTheWindow() {
        runner.withPropertyValues(
                "AUDIT_RETENTION_ENABLED=true",
                "AUDIT_RETENTION_DAYS=30",
                "AUDIT_RETENTION_BATCH_SIZE=250").run(context -> {
                    assertThat(context).hasNotFailed();

                    AuditRetentionProperties bound = context.getBean(AuditRetentionProperties.class);
                    assertThat(bound.isEnabled()).isTrue();
                    assertThat(bound.getDays()).isEqualTo(30);
                    assertThat(bound.getBatchSize()).isEqualTo(250);
                });
    }

    private static String readClasspathResource(String name) {
        try (InputStream stream = AuditRetentionPropertiesTest.class
                .getClassLoader().getResourceAsStream(name)) {
            assertThat(stream).as("%s must be on the test classpath", name).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new AssertionError("Unable to read " + name + " from the classpath", e);
        }
    }
}
