package com.nocteon.nocteon_api.auth.oauth2;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * GitHub's /user payload may expose a public email without proving ownership.
 * This asks /user/emails for the authenticated account's verified addresses
 * before any account is linked or created.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GithubEmailVerifier {

    private static final String EMAILS_URL = "https://api.github.com/user/emails";

    private final RestTemplate restTemplate;

    public Optional<String> verifiedEmail(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    EMAILS_URL, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {
                    });
            List<Map<String, Object>> emails = response.getBody();
            if (emails == null) {
                return Optional.empty();
            }
            return emails.stream()
                    .filter(e -> Boolean.TRUE.equals(e.get("verified")))
                    .filter(e -> Boolean.TRUE.equals(e.get("primary")))
                    .map(e -> String.valueOf(e.get("email")))
                    .filter(email -> !email.isBlank() && !"null".equals(email))
                    .findFirst()
                    .or(() -> emails.stream()
                            .filter(e -> Boolean.TRUE.equals(e.get("verified")))
                            .map(e -> String.valueOf(e.get("email")))
                            .filter(email -> !email.isBlank() && !"null".equals(email))
                            .findFirst());
        } catch (Exception e) {
            log.warn("Could not verify GitHub email ownership: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
