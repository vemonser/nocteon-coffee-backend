package com.nocteon.nocteon_api.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import com.nocteon.nocteon_api.auth.dto.response.AuthResponse;
import com.nocteon.nocteon_api.auth.dto.response.AuthResult;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.auth.service.AuthService;
import com.nocteon.nocteon_api.userActivity.service.LoginActivityService;

class OAuth2SuccessHandlerTest {

    @Test
    void redirectsWithAOneTimeCodeAndNeverTheAccessToken() throws Exception {
        AuthService authService = mock(AuthService.class);
        UserRepository userRepository = mock(UserRepository.class);
        LoginActivityService loginActivityService = mock(LoginActivityService.class);
        OAuth2CodeService codeService = mock(OAuth2CodeService.class);
        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(
                authService, userRepository, loginActivityService, codeService);
        ReflectionTestUtils.setField(handler, "frontendUrl", "https://frontend.example");
        ReflectionTestUtils.setField(handler, "refreshTokenExpiration", 60_000L);

        User user = User.builder()
                .id(7L)
                .email("user@example.com")
                .username("user")
                .role("CUSTOMER")
                .enabled(true)
                .isActive(true)
                .createdAt(Instant.now())
                .build();
        UserPrincipal principal = UserPrincipal.create(user);
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        when(authService.buildAuthResponseForOAuth2(user)).thenReturn(AuthResult.builder()
                .authResponse(AuthResponse.builder().accessToken("secret-access-token").build())
                .rawRefreshToken("raw-refresh")
                .build());
        when(codeService.issue(7L)).thenReturn("code-123");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "en");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("https://frontend.example/en/oauth2/callback?code=code-123")
                .doesNotContain("secret-access-token");
        assertThat(response.getHeader("Set-Cookie")).contains("refreshToken=raw-refresh");
        verify(userRepository).save(user);
        verify(codeService).issue(7L);
    }
}
