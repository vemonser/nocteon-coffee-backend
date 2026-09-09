package com.nocteon.nocteon_api.auth.oauth2;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.nocteon.nocteon_api.auth.dto.response.AuthResult;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.auth.service.AuthService;
import com.nocteon.nocteon_api.userActivity.service.LoginActivityService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final String DEFAULT_LOCALE = "en";
    private static final String ARABIC_LOCALE = "ar";

    private final AuthService authService;
    private final UserRepository userRepository;
    private final LoginActivityService loginActivityService;

    @Value("${application.frontend-url}")
    private String frontendUrl;

    @Value("${application.security.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = userPrincipal.getUser();

        // Update lastActiveAt on OAuth2 login
        user.setLastActiveAt(Instant.now());
        userRepository.save(user);

        // Record login activity (device breakdown)
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        loginActivityService.recordLogin(user, userAgent);

        AuthResult result = authService.buildAuthResponseForOAuth2(user);

        ResponseCookie cookie = ResponseCookie.from("refreshToken", result.getRawRefreshToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/api/auth")
                .maxAge(Duration.ofMillis(refreshTokenExpiration))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        String locale = resolveLocale(request);
        String redirectUrl = frontendUrl + "/" + locale + "/oauth2/callback?token=" + result.getAuthResponse().getAccessToken();
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    /**
     * Resolves locale from the Accept-Language header.
     * Supports "ar" (Arabic) and defaults to "en" (English).
     */
    private String resolveLocale(HttpServletRequest request) {
        String acceptLanguage = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return DEFAULT_LOCALE;
        }

        // Accept-Language format: "ar,en-US;q=0.9,en;q=0.8"
        // Parse the primary language tag (highest priority)
        String primary = acceptLanguage.split(",")[0].trim();
        String langTag = primary.contains(";")
                ? primary.split(";")[0].trim()
                : primary.trim();

        langTag = langTag.toLowerCase(Locale.ROOT);

        if (langTag.startsWith(ARABIC_LOCALE)) {
            return ARABIC_LOCALE;
        }
        return DEFAULT_LOCALE;
    }
}