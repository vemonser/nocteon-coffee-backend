package com.nocteon.nocteon_api.auth.oauth2;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Value("${application.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        String redirectUrl = frontendUrl + "/login?error=oauth2_failure";
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}