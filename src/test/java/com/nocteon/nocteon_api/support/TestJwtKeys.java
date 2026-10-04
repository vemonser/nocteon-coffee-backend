package com.nocteon.nocteon_api.support;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Throwaway RSA keypair for tests that boot the full Spring context.
 *
 * <p>{@code KeyConfig} resolves {@code JWT_PRIVATE_KEY} / {@code JWT_PUBLIC_KEY} from Spring
 * properties first and only falls back to {@code keys/private_key.pem} /
 * {@code keys/public_key.pem} on the classpath when those values are blank. Those PEM files are
 * gitignored — {@code .gitignore} lists both {@code src/main/resources/keys/} and {@code *.pem} —
 * so they are absent from a clean checkout and every {@code @SpringBootTest} fails while building
 * the {@code jwtPrivateKey} / {@code jwtPublicKey} beans.
 *
 * <p>No key material is committed, deliberately. A checked-in private key, even a test-only one,
 * is a secret-scanner hit and tends to get copied around. Instead the pair is generated in memory
 * once per JVM and injected as properties, which is exactly the resolution path production uses
 * when the keys come from the environment.
 */
public final class TestJwtKeys {

    private static final KeyPair KEY_PAIR = generateKeyPair();

    private TestJwtKeys() {
    }

    /**
     * Registers {@code JWT_PRIVATE_KEY} / {@code JWT_PUBLIC_KEY} on the supplied registry so the
     * context never has to reach for the gitignored classpath PEMs.
     */
    public static void register(DynamicPropertyRegistry registry) {
        registry.add("JWT_PRIVATE_KEY", () -> toPem("PRIVATE KEY", KEY_PAIR.getPrivate().getEncoded()));
        registry.add("JWT_PUBLIC_KEY", () -> toPem("PUBLIC KEY", KEY_PAIR.getPublic().getEncoded()));
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key generation is unavailable on this JVM", e);
        }
    }

    /**
     * PEM-wraps a DER-encoded key. The private key is emitted in PKCS#8 and the public key in
     * X.509, which are the two formats {@code KeyConfig} feeds to its {@code KeyFactory}.
     * {@code KeyConfig.readKey} strips the {@code -----BEGIN/END-----} markers and all whitespace
     * itself, so handing it the full PEM is safe.
     */
    private static String toPem(String label, byte[] der) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getEncoder().encodeToString(der)
                + "\n-----END " + label + "-----\n";
    }
}