package com.nocteon.nocteon_api.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * The authorization request is attacker-visible on the way back from the
 * provider. It must be an opaque id backed by Redis, never a Java-serialized
 * blob that the server deserializes from a cookie.
 */
@SpringBootTest(classes = NocteonApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CookieOAuth2AuthorizationRequestRepositoryTest {

    private static final String COOKIE = "oauth2_auth_request";

    @Autowired
    private CookieOAuth2AuthorizationRequestRepository repository;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setSecure(true);
        response = new MockHttpServletResponse();
    }

    private OAuth2AuthorizationRequest sample() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .clientId("test-client-id")
                .redirectUri("http://localhost:8080/login/oauth2/code/google")
                .scopes(Set.of("email", "profile"))
                .state("test-state")
                .additionalParameters(Map.<String, Object>of(
                        "code_challenge", "b1fsT5zKirEtHwiG9C8OkLRdnqBrAVu9qhwQGt2CvuM",
                        "code_challenge_method", "S256",
                        "code_verifier", "verifier-123",
                        "nonce", "nonce-123"))
                .attributes(Map.<String, Object>of("registration_id", "google"))
                .build();
    }

    @Test
    void saveAndLoadAuthorizationRequest() {
        repository.saveAuthorizationRequest(sample(), request, response);

        var cookie = response.getCookie(COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).matches("[0-9a-f-]{36}");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(response.getHeader("Set-Cookie")).contains("SameSite=Lax");

        request.setCookies(cookie);

        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(request);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getAuthorizationUri()).isEqualTo("https://accounts.google.com/o/oauth2/v2/auth");
        assertThat(loaded.getClientId()).isEqualTo("test-client-id");
        assertThat(loaded.getRedirectUri()).isEqualTo("http://localhost:8080/login/oauth2/code/google");
        assertThat(loaded.getScopes()).containsExactlyInAnyOrder("email", "profile");
        assertThat(loaded.getState()).isEqualTo("test-state");
        assertThat(loaded.getAdditionalParameters())
                .containsEntry("code_challenge_method", "S256")
                .containsEntry("code_verifier", "verifier-123")
                .containsEntry("nonce", "nonce-123");
        assertThat(loaded.getAttributes()).containsEntry("registration_id", "google");
    }

    @Test
    void removeAuthorizationRequestDeletesCookieAndRedisEntry() {
        repository.saveAuthorizationRequest(sample(), request, response);
        request.setCookies(response.getCookie(COOKIE));

        MockHttpServletResponse removeResponse = new MockHttpServletResponse();
        OAuth2AuthorizationRequest removed = repository.removeAuthorizationRequest(request, removeResponse);
        assertThat(removed).isNotNull();
        assertThat(removed.getState()).isEqualTo("test-state");

        assertThat(removeResponse.getHeader("Set-Cookie")).contains("Max-Age=0");
        assertThat(repository.loadAuthorizationRequest(request)).isNull();
    }

    @Test
    void loadReturnsNullWhenNoCookie() {
        assertThat(repository.loadAuthorizationRequest(request)).isNull();
    }

    @Test
    void loadReturnsNullWhenCookieIsInvalid() {
        request.setCookies(new jakarta.servlet.http.Cookie(COOKIE, "not-valid-base64!!!"));

        assertThat(repository.loadAuthorizationRequest(request)).isNull();
    }

    @Test
    void loadReturnsNullForAnUnknownUuidShapedCookie() {
        request.setCookies(new jakarta.servlet.http.Cookie(COOKIE, "00000000-0000-0000-0000-000000000000"));

        assertThat(repository.loadAuthorizationRequest(request)).isNull();
    }

    @Test
    void saveNullAuthorizationRequestRemovesExisting() {
        repository.saveAuthorizationRequest(null, request, response);

        assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0");
    }

    @Test
    void cookieIsAnOpaqueIdNotAJavaSerializationBlob() {
        repository.saveAuthorizationRequest(sample(), request, response);

        String value = response.getCookie(COOKIE).getValue();
        // Java serialization base64 starts with rO0; the new cookie must be a UUID.
        assertThat(value).matches("[0-9a-f-]{36}");
        assertThat(value).doesNotStartWith("rO0");
    }
}
