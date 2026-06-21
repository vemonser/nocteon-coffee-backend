package com.nocteon.nocteon_api.auth.oauth2;

import java.io.IOException;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.nocteon.nocteon_api.auth.dto.response.AuthResult;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.auth.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthService authService;

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
        AuthResult result = authService.buildAuthResponseForOAuth2(userPrincipal.getUser());

        ResponseCookie cookie = ResponseCookie.from("refreshToken", result.getRawRefreshToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/api/auth")
                .maxAge(Duration.ofMillis(refreshTokenExpiration))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        String redirectUrl = frontendUrl + "/oauth2/callback?token=" + result.getAuthResponse().getAccessToken();
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}