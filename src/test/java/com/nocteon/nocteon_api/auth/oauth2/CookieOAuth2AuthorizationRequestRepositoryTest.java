package com.nocteon.nocteon_api.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import jakarta.servlet.http.Cookie;

class CookieOAuth2AuthorizationRequestRepositoryTest {

    private CookieOAuth2AuthorizationRequestRepository repository;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        repository = new CookieOAuth2AuthorizationRequestRepository();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void saveAndLoadAuthorizationRequest() {
        OAuth2AuthorizationRequest authRequest = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .clientId("test-client-id")
                .redirectUri("http://localhost:8080/login/oauth2/code/google")
                .scope("email", "profile")
                .state("test-state")
                .additionalParameters(Map.of("key", "value"))
                .build();

        repository.saveAuthorizationRequest(authRequest, request, response);

        // Verify cookie was added
        Cookie[] cookies = response.getCookies();
        assertThat(cookies).hasSize(1);
        assertThat(cookies[0].getName()).isEqualTo("oauth2_auth_request");
        assertThat(cookies[0].getPath()).isEqualTo("/");
        assertThat(cookies[0].isHttpOnly()).isTrue();
        assertThat(cookies[0].getMaxAge()).isEqualTo(300);

        // Simulate the cookie being sent back on the callback
        request.setCookies(cookies[0]);

        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(request);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getAuthorizationUri()).isEqualTo("https://accounts.google.com/o/oauth2/v2/auth");
        assertThat(loaded.getClientId()).isEqualTo("test-client-id");
        assertThat(loaded.getRedirectUri()).isEqualTo("http://localhost:8080/login/oauth2/code/google");
        assertThat(loaded.getScopes()).containsExactlyInAnyOrder("email", "profile");
        assertThat(loaded.getState()).isEqualTo("test-state");
    }

    @Test
    void removeAuthorizationRequestDeletesCookie() {
        OAuth2AuthorizationRequest authRequest = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .clientId("test-client-id")
                .redirectUri("http://localhost:8080/login/oauth2/code/google")
                .scope("email")
                .state("test-state")
                .build();

        repository.saveAuthorizationRequest(authRequest, request, response);

        // Simulate callback
        Cookie savedCookie = response.getCookies()[0];
        request.setCookies(savedCookie);

        // Remove should return the request and clear the cookie
        OAuth2AuthorizationRequest removed = repository.removeAuthorizationRequest(request, response);
        assertThat(removed).isNotNull();
        assertThat(removed.getState()).isEqualTo("test-state");

        // Verify cookie deletion cookie was added (maxAge=0)
        Cookie[] deleteCookies = response.getCookies();
        assertThat(deleteCookies).isNotEmpty();
        Cookie deleteCookie = deleteCookies[deleteCookies.length - 1];
        assertThat(deleteCookie.getName()).isEqualTo("oauth2_auth_request");
        assertThat(deleteCookie.getMaxAge()).isEqualTo(0);
    }

    @Test
    void loadReturnsNullWhenNoCookie() {
        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(request);
        assertThat(loaded).isNull();
    }

    @Test
    void loadReturnsNullWhenCookieIsInvalid() {
        Cookie badCookie = new Cookie("oauth2_auth_request", "not-valid-base64!!!");
        request.setCookies(badCookie);

        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(request);
        assertThat(loaded).isNull();
    }

    @Test
    void saveNullAuthorizationRequestRemovesExisting() {
        repository.saveAuthorizationRequest(null, request, response);

        Cookie[] cookies = response.getCookies();
        assertThat(cookies).isNotEmpty();
        assertThat(cookies[cookies.length - 1].getMaxAge()).isEqualTo(0);
    }

    @Test
    void serializedRequestSurvivesBase64RoundTrip() throws Exception {
        OAuth2AuthorizationRequest authRequest = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .clientId("1014065285256-5pepapvlq4d76t5nna5tk3ttil11rd09.apps.googleusercontent.com")
                .redirectUri("http://localhost:8080/login/oauth2/code/google")
                .scope("email", "profile")
                .state("jF8IaEatCAURCg2qX7DrP_ol0t8mDz6G05p4UJs4BQ0=")
                .additionalParameters(Map.of(
                        "code_challenge", "b1fsT5zKirEtHwiG9C8OkLRdnqBrAVu9qhwQGt2CvuM",
                        "code_challenge_method", "S256"))
                .build();

        // Simulate what the repository does: serialize -> base64 -> deserialize
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos)) {
            oos.writeObject(authRequest);
        }
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(baos.toByteArray());

        byte[] decoded = Base64.getUrlDecoder().decode(encoded);
        try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(
                new java.io.ByteArrayInputStream(decoded))) {
            OAuth2AuthorizationRequest deserialized = (OAuth2AuthorizationRequest) ois.readObject();
            assertThat(deserialized.getAuthorizationUri())
                    .isEqualTo("https://accounts.google.com/o/oauth2/v2/auth");
            assertThat(deserialized.getState()).isEqualTo("jF8IaEatCAURCg2qX7DrP_ol0t8mDz6G05p4UJs4BQ0=");
            assertThat(deserialized.getAdditionalParameters()).containsKey("code_challenge");
        }
    }
}
