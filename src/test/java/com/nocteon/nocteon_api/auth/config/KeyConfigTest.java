package com.nocteon.nocteon_api.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.security.PrivateKey;
import java.security.PublicKey;

class KeyConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(KeyConfig.class);

    @Test
    void missingPrivateKeyFailsStartup() {
        contextRunner
                .withPropertyValues(validPublicKeyProperty())
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNotNull();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class);
                    assertThat(context.getStartupFailure().getMessage())
                            .contains("JWT_PRIVATE_KEY must be configured");
                });
    }

    @Test
    void missingPublicKeyFailsStartup() {
        contextRunner
                .withPropertyValues(validPrivateKeyProperty())
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNotNull();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class);
                    assertThat(context.getStartupFailure().getMessage())
                            .contains("JWT_PUBLIC_KEY must be configured");
                });
    }

    @Test
    void blankPrivateKeyFailsStartup() {
        contextRunner
                .withPropertyValues("JWT_PRIVATE_KEY=   ", validPublicKeyProperty())
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNotNull();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class);
                    assertThat(context.getStartupFailure().getMessage())
                            .contains("JWT_PRIVATE_KEY must be configured");
                });
    }

    @Test
    void blankPublicKeyFailsStartup() {
        contextRunner
                .withPropertyValues(validPrivateKeyProperty(), "JWT_PUBLIC_KEY=")
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNotNull();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class);
                    assertThat(context.getStartupFailure().getMessage())
                            .contains("JWT_PUBLIC_KEY must be configured");
                });
    }

    @Test
    void validSuppliedKeysCreateBeans() {
        String[] keys = generateKeyPems();
        contextRunner
                .withPropertyValues(keys[0], keys[1])
                .run(context -> {
                    assertThat(context).hasSingleBean(PrivateKey.class);
                    assertThat(context).hasSingleBean(PublicKey.class);
                    assertThat(context.getBean(PrivateKey.class)).isNotNull();
                    assertThat(context.getBean(PublicKey.class)).isNotNull();
                });
    }

    private static String validPrivateKeyProperty() {
        return "JWT_PRIVATE_KEY=" + generateKeyPems()[0].substring("JWT_PRIVATE_KEY=".length());
    }

    private static String validPublicKeyProperty() {
        return "JWT_PUBLIC_KEY=" + generateKeyPems()[1].substring("JWT_PUBLIC_KEY=".length());
    }

    private static String[] generateKeyPems() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            String privatePem = "JWT_PRIVATE_KEY=-----BEGIN PRIVATE KEY-----"
                    + Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())
                    + "-----END PRIVATE KEY-----";
            String publicPem = "JWT_PUBLIC_KEY=-----BEGIN PUBLIC KEY-----"
                    + Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded())
                    + "-----END PUBLIC KEY-----";
            return new String[] { privatePem, publicPem };
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate test JWT key pair", e);
        }
    }
}
