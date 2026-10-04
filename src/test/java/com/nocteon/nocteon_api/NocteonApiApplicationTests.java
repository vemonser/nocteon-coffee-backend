package com.nocteon.nocteon_api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * Boots the whole application context.
 *
 * <p>This is an integration test, so it needs a PostgreSQL and a Redis that are already migrated by
 * {@code src/main/resources/db/migration}. On a developer box whose database predates
 * {@code V10__enable_pgvector.sql} this test fails with
 * {@code FlywayValidateException: Detected resolved migration not applied to database: 10} — the
 * schema is out of date, not the test. Start the stack with {@code docker compose up -d} against a
 * fresh volume to run it.
 *
 * <p>The JWT keypair, by contrast, is self-contained and is supplied below.
 */
@SpringBootTest
class NocteonApiApplicationTests {

	/**
	 * {@code KeyConfig} reads {@code JWT_PRIVATE_KEY} / {@code JWT_PUBLIC_KEY} from Spring
	 * properties before falling back to the gitignored classpath PEMs, so a throwaway
	 * runtime-generated pair is enough to get the context past key loading without committing any
	 * key material.
	 */
	@DynamicPropertySource
	static void jwtKeys(DynamicPropertyRegistry registry) {
		TestJwtKeys.register(registry);
	}

	@Test
	void contextLoads() {
	}

}
