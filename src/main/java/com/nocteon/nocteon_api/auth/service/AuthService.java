package com.nocteon.nocteon_api.auth.service;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.auth.dto.request.LoginRequest;
import com.nocteon.nocteon_api.auth.dto.request.RegisterRequest;
import com.nocteon.nocteon_api.auth.dto.response.AuthResponse;
import com.nocteon.nocteon_api.auth.dto.response.AuthResult;
import com.nocteon.nocteon_api.auth.dto.response.RegisterResponse;
import com.nocteon.nocteon_api.auth.dto.response.UserResponse;
import com.nocteon.nocteon_api.auth.entity.RefreshToken;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.entity.UserProfile;
import com.nocteon.nocteon_api.auth.enums.Provider;
import com.nocteon.nocteon_api.auth.enums.Role;
import com.nocteon.nocteon_api.auth.repository.RefreshTokenRepository;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.repository.UserProfileRepository;
import com.nocteon.nocteon_api.auth.security.JwtService;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.common.enums.Permission;
import com.nocteon.nocteon_api.common.exception.UnauthorizedException;
import com.nocteon.nocteon_api.common.exception.account.AccountNotVerifiedException;
import com.nocteon.nocteon_api.common.exception.email.EmailAlreadyExistsException;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidCredentialsException;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidRefreshTokenException;
import com.nocteon.nocteon_api.common.exception.user.UsernameAlreadyExistsException;
import com.nocteon.nocteon_api.common.redis.TokenBlacklistService;
import com.nocteon.nocteon_api.common.util.PasswordValidator;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final EmailVerificationService emailVerificationService;
    private final LoginAttemptService loginAttemptService;
    private final TokenBlacklistService tokenBlacklistService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;

    @Value("${application.security.jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${application.security.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException();
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UsernameAlreadyExistsException();
        }

        passwordValidator.validate(request.getPassword());

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .provider(Provider.LOCAL)
                .role(Role.CUSTOMER)
                .enabled(false)
                .build();
        user = userRepository.save(user);

        UserProfile profile = UserProfile.builder()
                .user(user)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .build();
        userProfileRepository.save(profile);

        emailVerificationService.sendVerificationEmail(user);

        return RegisterResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .message("Registration successful. Please check your email to verify your account.")
                .build();
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        User user = userRepository.findByEmailOrUsername(request.getIdentifier())
                .orElseThrow(InvalidCredentialsException::new);

        loginAttemptService.checkAccountNotLocked(user);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginAttemptService.handleFailedLogin(user);
            throw new InvalidCredentialsException();
        }

        if (!user.isEnabled()) {
            throw new AccountNotVerifiedException();
        }

        user.resetFailedLogin();
        userRepository.save(user);

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResult refreshToken(String rawRefreshToken) {

        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException();
        }

        String tokenHash = jwtService.hashToken(rawRefreshToken);

        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (storedToken.isRevoked()) {
            refreshTokenRepository.deleteByUserId(storedToken.getUser().getId());
            throw new InvalidRefreshTokenException();
        }

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(storedToken);
            throw new InvalidRefreshTokenException();
        }

        User user = storedToken.getUser();

        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        return buildAuthResponse(user);
    }

    @Transactional
    public void logout(String rawRefreshToken, String accessToken) {

        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String tokenHash = jwtService.hashToken(rawRefreshToken);
            refreshTokenRepository.deleteByTokenHash(tokenHash);
        }

        if (accessToken != null && !accessToken.isBlank()) {
            tokenBlacklistService.blacklist(accessToken);
        }
    }

    @Transactional
    public void logoutAllDevices(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    private AuthResult buildAuthResponse(User user) {
        User userWithProfile = userRepository.findByIdWithProfile(user.getId())
                .orElse(user);
        UserProfile profile = userWithProfile.getProfile();

        UserPrincipal principal = UserPrincipal.create(userWithProfile);
        String accessToken = jwtService.generateAccessToken(principal);

        String rawRefreshToken = jwtService.generateRawRefreshToken();
        String tokenHash = jwtService.hashToken(rawRefreshToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(tokenHash)
                .user(userWithProfile)
                .expiresAt(Instant.now().plusMillis(refreshTokenExpiration))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        List<String> permissions = userWithProfile.getRole()
                .getPermissions()
                .stream()
                .map(Permission::getPermission)
                .toList();

        return AuthResult.builder()
                .authResponse(AuthResponse.builder()
                        .accessToken(accessToken)
                        .expiresIn(accessTokenExpiration / 1000)
                        .user(UserResponse.builder()
                                .id(userWithProfile.getId())
                                .email(userWithProfile.getEmail())
                                .username(userWithProfile.getUsername())
                                .role(userWithProfile.getRole())
                                .firstName(profile != null ? profile.getFirstName() : null)
                                .lastName(profile != null ? profile.getLastName() : null)
                                .avatarUrl(profile != null ? profile.getAvatarUrl() : null)
                                .permissions(permissions)
                                .build())
                        .build())
                .rawRefreshToken(rawRefreshToken)
                .build();
    }

    public AuthResult buildAuthResponseForOAuth2(User user) {
        return buildAuthResponse(user);
    }
}