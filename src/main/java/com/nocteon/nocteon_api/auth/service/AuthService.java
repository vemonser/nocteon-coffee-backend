package com.nocteon.nocteon_api.auth.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.audit.enums.AuditAction;
import com.nocteon.nocteon_api.audit.service.AuditLogService;
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
import com.nocteon.nocteon_api.auth.exception.AccountDisabledException;
import com.nocteon.nocteon_api.auth.exception.AccountLockedException;
import com.nocteon.nocteon_api.auth.repository.RefreshTokenRepository;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.repository.UserProfileRepository;
import com.nocteon.nocteon_api.auth.security.JwtService;
import com.nocteon.nocteon_api.auth.oauth2.OAuth2CodeService;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.common.enums.Permission;
import com.nocteon.nocteon_api.common.exception.UnauthorizedException;
import com.nocteon.nocteon_api.common.security.AuditActor;
import com.nocteon.nocteon_api.common.exception.account.AccountNotVerifiedException;
import com.nocteon.nocteon_api.common.exception.email.EmailAlreadyExistsException;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidCredentialsException;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidRefreshTokenException;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidVerificationException;
import com.nocteon.nocteon_api.common.exception.user.UsernameAlreadyExistsException;
import com.nocteon.nocteon_api.common.redis.TokenBlacklistService;
import com.nocteon.nocteon_api.common.util.PasswordValidator;
import com.nocteon.nocteon_api.userActivity.service.LoginActivityService;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {
    private final ApplicationEventPublisher eventPublisher;

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final EmailVerificationService emailVerificationService;
    private final LoginAttemptService loginAttemptService;
    private final TokenBlacklistService tokenBlacklistService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;
    private final LoginActivityService loginActivityService;
    private final OAuth2CodeService oauth2CodeService;
    private final AuditLogService auditLogService;

    @Value("${application.security.jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${application.security.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String username = request.getUsername().trim();
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException();
        }
        if (userRepository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException();
        }

        passwordValidator.validate(request.getPassword());

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .provider(Provider.LOCAL)
                .role("CUSTOMER")
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

        String fullName = (request.getFirstName() + " " + request.getLastName()).trim();
        eventPublisher.publishEvent(new com.nocteon.nocteon_api.notifications.event.UserRegisteredEvent(user.getId(), fullName));

        return RegisterResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .message("Registration successful. Please check your email to verify your account.")
                .build();
    }

    /**
     * A refused sign-in changes nothing — no token, no counter on the account-state
     * refusals — so the refusal does not roll the transaction back: the audit row
     * recording the attempt (support's "the customer says they cannot log in") is
     * the only write, and it has to survive the refusal it describes.
     */
    @Transactional(noRollbackFor = {
            InvalidCredentialsException.class,
            AccountNotVerifiedException.class,
            AccountDisabledException.class,
            AccountLockedException.class
    })
    public AuthResult login(LoginRequest request, String userAgent) {
        String identifier = request.getIdentifier().trim();
        User user = userRepository.findByEmailOrUsernameForUpdate(identifier).orElse(null);
        if (user == null) {
            // Nothing identifies the caller: the row keeps the attempted identifier in
            // metadata and records NO actor, because a wrong password proves nothing
            // about who typed it.
            auditLoginFailure(null, identifier, null, "No account matches this identifier.");
            throw new InvalidCredentialsException();
        }

        try {
            loginAttemptService.checkAccountNotLocked(user);
        } catch (AccountLockedException e) {
            auditLoginFailure(user, identifier, null, "The account is locked.");
            throw e;
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginAttemptService.handleFailedLogin(user);
            auditLoginFailure(user, identifier, user.getFailedLoginAttempts(), "The password did not match.");
            throw new InvalidCredentialsException();
        }

        if (!user.isEnabled()) {
            auditLoginFailure(user, identifier, null, "The account has not verified its e-mail.");
            throw new AccountNotVerifiedException();
        }
        if (!user.isActive()) {
            auditLoginFailure(user, identifier, null, "The account is blocked.");
            throw new AccountDisabledException();
        }

        if (user.isLocked()) {
            auditLoginFailure(user, identifier, null, "The account is locked.");
            throw new AccountLockedException();
        }
        user.resetFailedLogin();
        user.setLastActiveAt(Instant.now());
        userRepository.save(user);

        loginActivityService.recordLogin(user, userAgent);
        auditLoginSuccess(user);
        return buildAuthResponse(user);
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthResult refreshToken(String rawRefreshToken) {

        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException();
        }

        String tokenHash = jwtService.hashToken(rawRefreshToken);

        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (storedToken.isRevoked()) {
            // Reuse of a rotated token: treat it as theft and invalidate every
            // access token for the account as well, not just refresh tokens.
            User compromised = storedToken.getUser();
            compromised.incrementTokenVersion();
            userRepository.save(compromised);
            refreshTokenRepository.deleteByUserId(compromised.getId());
            throw new InvalidRefreshTokenException();
        }

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(storedToken);
            throw new InvalidRefreshTokenException();
        }

        User user = storedToken.getUser();
        if (!user.isEnabled()) {
            throw new AccountNotVerifiedException();
        }
        if (!user.isActive()) {
            throw new AccountDisabledException();
        }
        if (user.isLocked()) {
            throw new AccountLockedException();
        }

        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        return buildAuthResponse(user);
    }

    /**
     * End this session.
     *
     * <p>The audit row is written only when the caller is authenticated: a logout
     * with an already-expired access token cannot name the account whose refresh
     * token was dropped, and a row with no actor and no subject would explain
     * nothing. The access token is never recorded — it is a credential.
     */
    @Transactional
    public void logout(String rawRefreshToken, String accessToken, UserPrincipal principal) {

        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String tokenHash = jwtService.hashToken(rawRefreshToken);
            refreshTokenRepository.deleteByTokenHash(tokenHash);
        }

        if (accessToken != null && !accessToken.isBlank()) {
            tokenBlacklistService.blacklist(accessToken);
        }

        AuditActor actor = AuditActor.of(principal);
        if (actor.userId() != null) {
            auditLogService.logSuccess(AuditAction.USER_LOGOUT, actor.userId(), actor.userEmail(),
                    "User", actor.userId(), null, null, null, null, null, null);
        }
    }

    @Transactional
    public void logoutAllDevices(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            user.incrementTokenVersion();
            userRepository.save(user);
            // The account itself is the actor: it authorised the revocation of every
            // session it had. No principal threading needed — the row names the user.
            auditLogService.logSuccess(AuditAction.USER_LOGOUT_ALL, user.getId(), user.getEmail(),
                    "User", user.getId(), null, null, null, null, null, null);
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse exchangeOAuth2Code(String code) {
        Long userId = oauth2CodeService.consume(code);
        User user = userRepository.findByIdWithProfile(userId)
                .orElseThrow(InvalidVerificationException::new);
        if (!user.isEnabled()) {
            throw new AccountNotVerifiedException();
        }
        if (!user.isActive()) {
            throw new AccountDisabledException();
        }
        if (user.isLocked()) {
            throw new AccountLockedException();
        }
        return buildAccessResponse(user);
    }

    // ─── the audit rows ──────────────────────────────────────────────────────

    /**
     * "This account signed in."
     *
     * <p>The actor IS the user when the password matched — that is what the match
     * proves. No request metadata (no IP, no user agent) is recorded here: the
     * writer already accepts both, and filling them is another branch's job.
     */
    private void auditLoginSuccess(User user) {
        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("role", user.getRole());
        newValues.put("provider", user.getProvider() == null ? null : user.getProvider().name());

        auditLogService.logSuccess(AuditAction.USER_LOGIN, user.getId(), user.getEmail(),
                "User", user.getId(), null, null, null, null, newValues, null);
    }

    /**
     * "Somebody tried to sign in as this account and could not."
     *
     * <p>The row survives the refusal because {@code login} does not roll back for
     * the exception it is about to throw — a refused sign-in writes nothing else,
     * so the transaction commits with this row as its only write.
     *
     * <p>The actor is left null on purpose. A wrong password proves nothing about
     * who was typing, and inventing an actor is worse than recording none; the
     * entity columns name the account that was attempted.
     */
    private void auditLoginFailure(User target, String identifier, Integer failedAttempts, String reason) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (target == null) {
            // No account matched: the typed identifier is the only clue, and it is
            // not an actor (it may be someone else's address, or a typo).
            metadata.put("identifier", identifier);
        } else if (failedAttempts != null) {
            metadata.put("failedAttempts", failedAttempts);
        }

        // No old/new values: nothing in the account changed. The row is the
        // ATTEMPT, and the failure is the whole of what it has to say.
        auditLogService.logFailure(AuditAction.USER_LOGIN,
                null, null,
                "User", target == null ? null : target.getId(), null,
                null, null, null, null, metadata, reason);
    }

    private AuthResult buildAuthResponse(User user) {
        User userWithProfile = userRepository.findByIdWithProfile(user.getId())
                .orElse(user);
        AuthResponse accessResponse = buildAccessResponse(userWithProfile);

        String rawRefreshToken = jwtService.generateRawRefreshToken();
        String tokenHash = jwtService.hashToken(rawRefreshToken);
        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(tokenHash)
                .user(userWithProfile)
                .expiresAt(Instant.now().plusMillis(refreshTokenExpiration))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        return AuthResult.builder()
                .authResponse(accessResponse)
                .rawRefreshToken(rawRefreshToken)
                .build();
    }

    private AuthResponse buildAccessResponse(User user) {
        User userWithProfile = user.getProfile() != null
                ? user
                : userRepository.findByIdWithProfile(user.getId()).orElse(user);
        UserProfile profile = userWithProfile.getProfile();
        UserPrincipal principal = UserPrincipal.create(userWithProfile);
        String accessToken = jwtService.generateAccessToken(principal);

        List<String> permissions;
        com.nocteon.nocteon_api.auth.service.RoleService rs =
                com.nocteon.nocteon_api.auth.service.RoleService.getInstance();
        if (rs != null) {
            permissions = new java.util.ArrayList<>(rs.getPermissions(userWithProfile.getRole()));
        } else {
            permissions = java.util.List.of();
        }

        return AuthResponse.builder()
                .accessToken(accessToken)
                .expiresIn(accessTokenExpiration / 1000)
                .user(UserResponse.builder()
                        .id(userWithProfile.getId())
                        .email(userWithProfile.getEmail())
                        .username(userWithProfile.getUsername())
                        .role(userWithProfile.getRole())
                        .enabled(userWithProfile.isEnabled())
                        .isActive(userWithProfile.isActive())
                        .firstName(profile != null ? profile.getFirstName() : null)
                        .lastName(profile != null ? profile.getLastName() : null)
                        .avatarUrl(profile != null ? profile.getAvatarUrl() : null)
                        .createdAt(userWithProfile.getCreatedAt())
                        .lastActiveAt(userWithProfile.getLastActiveAt())
                        .permissions(permissions)
                        .build())
                .build();
    }

    public AuthResult buildAuthResponseForOAuth2(User user) {
        return buildAuthResponse(user);
    }
}