package com.nocteon.nocteon_api.auth.controller;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nocteon.nocteon_api.auth.dto.request.ForgotPasswordRequest;
import com.nocteon.nocteon_api.auth.dto.request.LoginRequest;
import com.nocteon.nocteon_api.auth.dto.request.RegisterRequest;
import com.nocteon.nocteon_api.auth.dto.request.ResetPasswordRequest;
import com.nocteon.nocteon_api.auth.dto.request.VerifyEmailRequest;
import com.nocteon.nocteon_api.auth.dto.response.AuthResponse;
import com.nocteon.nocteon_api.auth.dto.response.AuthResult;
import com.nocteon.nocteon_api.auth.dto.response.RegisterResponse;
import com.nocteon.nocteon_api.auth.service.AuthService;
import com.nocteon.nocteon_api.auth.service.EmailVerificationService;
import com.nocteon.nocteon_api.auth.service.PasswordResetService;
import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.exception.RateLimitExceededException;
import com.nocteon.nocteon_api.common.exception.UnauthorizedException;
import com.nocteon.nocteon_api.common.ratelimit.RateLimiterService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final RateLimiterService rateLimiterService;

    @Value("${application.security.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        if (!rateLimiterService.tryConsumeRegister(ip)) {
            throw new RateLimitExceededException();
        }
        RegisterResponse response = authService.register(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Registration successful"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();

        if (!rateLimiterService.tryConsumeLogin(ip, request.getIdentifier())) {
            throw new RateLimitExceededException();
        }

        AuthResult result = authService.login(request);
        ResponseCookie cookie = buildRefreshTokenCookie(result.getRawRefreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success(result.getAuthResponse(), "Login successful"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(name = "refreshToken", required = false) String rawRefreshToken) {
        AuthResult result = authService.refreshToken(rawRefreshToken);
        ResponseCookie cookie = buildRefreshTokenCookie(result.getRawRefreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success(result.getAuthResponse(), "Token refreshed"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = "refreshToken", required = false) String rawRefreshToken,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String accessToken = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }
        authService.logout(rawRefreshToken, accessToken);

        ResponseCookie expiredCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/api/auth")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .body(ApiResponse.success(null, "Logged out successfully"));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verifyEmail(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Email verified successfully"));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @RequestParam String email) {
        emailVerificationService.resendVerificationEmail(email);
        return ResponseEntity.ok(ApiResponse.success(null, "Verification email resent"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.forgotPassword(request);
        return ResponseEntity.ok(
                ApiResponse.success(null, "If an account exists with this email, a reset code has been sent"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password reset successfully"));
    }

    private ResponseCookie buildRefreshTokenCookie(String rawRefreshToken) {
        return ResponseCookie.from("refreshToken", rawRefreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/api/auth")
                .maxAge(Duration.ofMillis(refreshTokenExpiration))
                .build();
    }
}