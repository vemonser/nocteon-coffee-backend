package com.nocteon.nocteon_api.auth.oauth2;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Persists the OAuth2 authorization request in Redis and stores only an opaque
 * random id in the browser cookie.
 *
 * <p>The previous implementation stored a Java-serialized object in the cookie
 * and deserialized it with {@link java.io.ObjectInputStream}. That is a remote
 * code execution surface: the cookie is fully attacker-controlled, and
 * deserialization runs before any type check. Redis + JSON removes it.
 *
 * <p>The id is single-use: {@link #removeAuthorizationRequest} deletes the Redis
 * key and the cookie. Entries expire after five minutes.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String COOKIE_NAME = "oauth2_auth_request";
    private static final String KEY_PREFIX = "oauth2:authreq:";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final Pattern OPAQUE_ID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Nullable
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String id = readCookie(request);
        if (id == null) {
            return null;
        }
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + id);
            if (json == null) {
                return null;
            }
            StoredRequest stored = objectMapper.readValue(json, StoredRequest.class);
            return toAuthorizationRequest(stored);
        } catch (Exception e) {
            log.warn("Discarding unreadable OAuth2 authorization request");
            redisTemplate.delete(KEY_PREFIX + id);
            return null;
        }
    }

    @Override
    public void saveAuthorizationRequest(
            @Nullable OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (authorizationRequest == null) {
            removeAuthorizationRequest(request, response);
            return;
        }

        String id = UUID.randomUUID().toString();
        try {
            StoredRequest stored = new StoredRequest(
                    authorizationRequest.getAuthorizationUri(),
                    authorizationRequest.getClientId(),
                    authorizationRequest.getRedirectUri(),
                    authorizationRequest.getScopes(),
                    authorizationRequest.getState(),
                    authorizationRequest.getAdditionalParameters(),
                    authorizationRequest.getAttributes());
            redisTemplate.opsForValue().set(KEY_PREFIX + id, objectMapper.writeValueAsString(stored), TTL);
            response.addHeader(HttpHeaders.SET_COOKIE, cookie(id, request).toString());
        } catch (Exception e) {
            log.error("Failed to save OAuth2 authorization request", e);
        }
    }

    @Override
    @Nullable
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response) {
        String id = readCookie(request);
        String json = id == null ? null : redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + id);
        deleteCookie(request, response);
        if (json == null) {
            return null;
        }
        try {
            return toAuthorizationRequest(objectMapper.readValue(json, StoredRequest.class));
        } catch (Exception e) {
            log.warn("Discarding unreadable OAuth2 authorization request");
            return null;
        }
    }

    private OAuth2AuthorizationRequest toAuthorizationRequest(StoredRequest stored) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(stored.authorizationUri())
                .clientId(stored.clientId())
                .redirectUri(stored.redirectUri())
                .scopes(stored.scopes())
                .state(stored.state())
                .additionalParameters(stored.additionalParameters())
                .attributes(stored.attributes())
                .build();
    }

    private ResponseCookie cookie(String value, HttpServletRequest request) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(TTL)
                .build();
    }

    private void deleteCookie(HttpServletRequest request, HttpServletResponse response) {
        ResponseCookie expired = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());
    }

    @Nullable
    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null
                    && OPAQUE_ID.matcher(cookie.getValue()).matches()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private record StoredRequest(
            String authorizationUri,
            String clientId,
            String redirectUri,
            Set<String> scopes,
            String state,
            Map<String, Object> additionalParameters,
            Map<String, Object> attributes) {
    }
}
