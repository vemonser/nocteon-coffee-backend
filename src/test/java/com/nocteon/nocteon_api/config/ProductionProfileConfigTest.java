package com.nocteon.nocteon_api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * The prod profile is what Render activates. These assertions keep the noisy SQL
 * bind logging (PII) and the public API explorer out of production.
 */
@SpringBootTest(classes = NocteonApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("prod")
class ProductionProfileConfigTest {

    @Autowired
    private Environment environment;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Test
    void sqlBindLoggingIsNotTraceInProduction() {
        assertThat(environment.getProperty("logging.level.[org.hibernate.SQL]")).isEqualTo("INFO");
        assertThat(environment.getProperty("logging.level.[org.hibernate.orm.jdbc.bind]"))
                .isEqualTo("INFO");
    }

    @Test
    void openInViewIsDisabled() {
        assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
    }

    @Test
    void springdocIsDisabledInProduction() {
        assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isFalse();
    }
}
