package com.nocteon.nocteon_api.auth.config;

import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

@Configuration
public class KeyConfig {

    @Value("${JWT_PRIVATE_KEY:}")
    private String jwtPrivateKeyEnv;

    @Value("${JWT_PUBLIC_KEY:}")
    private String jwtPublicKeyEnv;

    @Bean
    public PrivateKey jwtPrivateKey() throws Exception {
        String pem = requireNonBlank(jwtPrivateKeyEnv, "JWT_PRIVATE_KEY");
        return KeyFactory.getInstance("RSA")
                .generatePrivate(
                        new PKCS8EncodedKeySpec(
                                Base64.getDecoder()
                                        .decode(stripPem(pem, "PRIVATE KEY"))));
    }

    @Bean
    public PublicKey jwtPublicKey() throws Exception {
        String pem = requireNonBlank(jwtPublicKeyEnv, "JWT_PUBLIC_KEY");
        return KeyFactory.getInstance("RSA")
                .generatePublic(
                        new X509EncodedKeySpec(
                                Base64.getDecoder()
                                        .decode(stripPem(pem, "PUBLIC KEY"))));
    }

    private static String requireNonBlank(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    propertyName + " must be configured with a non-blank PEM-encoded key; "
                            + "the classpath fallback is not supported");
        }
        return value;
    }

    private static String stripPem(String pem, String label) {
        return pem
                .replace("-----BEGIN " + label + "-----", "")
                .replace("-----END " + label + "-----", "")
                .replaceAll("\\s+", "");
    }
}
