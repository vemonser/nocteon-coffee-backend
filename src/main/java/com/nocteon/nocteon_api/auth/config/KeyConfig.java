package com.nocteon.nocteon_api.auth.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeyConfig {

    @Value("${JWT_PRIVATE_KEY:}")
    private String jwtPrivateKeyEnv;

    @Value("${JWT_PUBLIC_KEY:}")
    private String jwtPublicKeyEnv;

    @Bean
    public PrivateKey jwtPrivateKey() throws Exception {
        return KeyFactory.getInstance("RSA")
                .generatePrivate(
                        new PKCS8EncodedKeySpec(
                                Base64.getDecoder()
                                        .decode(readKey(jwtPrivateKeyEnv, "keys/private_key.pem"))));
    }

    @Bean
    public PublicKey jwtPublicKey() throws Exception {
        return KeyFactory.getInstance("RSA")
                .generatePublic(
                        new X509EncodedKeySpec(
                                Base64.getDecoder()
                                        .decode(readKey(jwtPublicKeyEnv, "keys/public_key.pem"))));
    }

    private String readKey(String envValue, String classpathPath) throws IOException {

        String pem = (envValue != null && !envValue.isBlank()) ? envValue : new String(
                new ClassPathResource(classpathPath)
                        .getInputStream()
                        .readAllBytes(),
                StandardCharsets.UTF_8);

        return pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
    }
}