package com.nocteon.nocteon_api.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class GithubEmailVerifierTest {

    private final RestTemplate restTemplate = new RestTemplate();
    private final GithubEmailVerifier verifier = new GithubEmailVerifier(restTemplate);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

    @Test
    void returnsThePrimaryVerifiedEmail() {
        server.expect(requestTo("https://api.github.com/user/emails"))
                .andExpect(header("Authorization", "Bearer token-123"))
                .andRespond(withSuccess("""
                        [
                          {"email":"user@example.com","primary":true,"verified":true},
                          {"email":"other@example.com","primary":false,"verified":true}
                        ]
                        """, MediaType.APPLICATION_JSON));

        assertThat(verifier.verifiedEmail("token-123")).contains("user@example.com");
    }

    @Test
    void fallsBackToAnyVerifiedEmailWhenNoneIsPrimary() {
        server.expect(requestTo("https://api.github.com/user/emails"))
                .andRespond(withSuccess("""
                        [
                          {"email":"unverified@example.com","primary":false,"verified":false},
                          {"email":"verified@example.com","primary":false,"verified":true}
                        ]
                        """, MediaType.APPLICATION_JSON));

        assertThat(verifier.verifiedEmail("token-123")).contains("verified@example.com");
    }

    @Test
    void ignoresUnverifiedEmails() {
        server.expect(requestTo("https://api.github.com/user/emails"))
                .andRespond(withSuccess("""
                        [{"email":"user@example.com","primary":true,"verified":false}]
                        """, MediaType.APPLICATION_JSON));

        assertThat(verifier.verifiedEmail("token-123")).isEmpty();
    }

    @Test
    void providerFailureFailsClosed() {
        server.expect(requestTo("https://api.github.com/user/emails"))
                .andRespond(withServerError());

        assertThat(verifier.verifiedEmail("token-123")).isEmpty();
    }

    @Test
    void googleRequiresTheEmailVerifiedClaim() {
        assertThat(new GoogleOAuth2UserInfo(Map.of("email_verified", true)).isEmailVerified()).isTrue();
        assertThat(new GoogleOAuth2UserInfo(Map.of("email_verified", false)).isEmailVerified()).isFalse();
        assertThat(new GoogleOAuth2UserInfo(Map.of()).isEmailVerified()).isFalse();
    }
}
