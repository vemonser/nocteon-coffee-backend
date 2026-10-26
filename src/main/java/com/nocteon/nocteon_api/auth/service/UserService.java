package com.nocteon.nocteon_api.auth.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.auth.exception.CannotChangeOwnRoleException;
import com.nocteon.nocteon_api.auth.exception.LastAdminRequiredException;
import com.nocteon.nocteon_api.auth.exception.UnknownRoleException;
import com.nocteon.nocteon_api.auth.dto.request.AdminRegisterRequest;
import com.nocteon.nocteon_api.auth.dto.request.AdminUpdateUserRequest;
import com.nocteon.nocteon_api.auth.dto.request.ChangeRoleRequest;
import com.nocteon.nocteon_api.auth.dto.request.ChangePasswordRequest;
import com.nocteon.nocteon_api.auth.dto.request.UpdateProfileRequest;
import com.nocteon.nocteon_api.auth.dto.request.UserFilterRequest;
import com.nocteon.nocteon_api.auth.dto.response.UserResponse;
import com.nocteon.nocteon_api.auth.dto.response.UserActivityResponse;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.entity.UserProfile;
import com.nocteon.nocteon_api.auth.enums.Role;
import com.nocteon.nocteon_api.auth.repository.UserProfileRepository;
import com.nocteon.nocteon_api.auth.repository.RoleRepository;
import com.nocteon.nocteon_api.audit.service.AuditLogService;
import com.nocteon.nocteon_api.audit.enums.AuditAction;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.exception.RoleChangeForbiddenException;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.cloudinary.service.CloudinaryService;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.dto.Summary;
import com.nocteon.nocteon_api.common.dto.Summary;
import com.nocteon.nocteon_api.common.enums.Permission;
import com.nocteon.nocteon_api.common.exception.email.EmailAlreadyExistsException;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidCredentialsException;
import com.nocteon.nocteon_api.common.exception.notFound.UserNotFoundException;
import com.nocteon.nocteon_api.common.exception.user.PasswordMismatchException;
import com.nocteon.nocteon_api.common.exception.user.UsernameAlreadyExistsException;
import com.nocteon.nocteon_api.common.util.PasswordValidator;
import java.util.List;
import com.nocteon.nocteon_api.dashboard.dto.UserGrowthDto;
import com.nocteon.nocteon_api.common.repository.SearchPatterns;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditLogService auditLogService;
    private final OrderRepository orderRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final CloudinaryService cloudinaryService;
    private final PasswordValidator passwordValidator;
    private final java.util.List<String> sensitivePermissions;
    @Transactional(readOnly = true)
    public UserResponse getMe(UserPrincipal principal) {
        return getById(principal.getUserId());
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        User user = userRepository.findByIdWithProfile(id)
                .orElseThrow(() -> new UserNotFoundException());
        return mapToResponse(user);
    }

    @Transactional
    public UserResponse updateProfile(UserPrincipal principal, UpdateProfileRequest request) {
        User user = userRepository.findByIdWithProfile(principal.getUserId())
                .orElseThrow(UserNotFoundException::new);

        UserProfile profile = user.getProfile();

        if (profile == null) {
            profile = UserProfile.builder().user(user).build();
            user.setProfile(profile);
        }
        if (request.getFirstName() != null)
            profile.setFirstName(request.getFirstName());
        if (request.getLastName() != null)
            profile.setLastName(request.getLastName());
        if (request.getPhone() != null)
            profile.setPhone(request.getPhone());
        if (request.getBio() != null)
            profile.setBio(request.getBio());

        userProfileRepository.save(profile);

        log.info("Profile updated for user: {}", user.getEmail());

        return mapToResponse(user);
    }

    @Transactional
    public UserResponse updateAvatar(UserPrincipal principal, MultipartFile file) {
        User user = userRepository.findByIdWithProfile(principal.getUserId())
                .orElseThrow(UserNotFoundException::new);

        UserProfile profile = user.getProfile();

        if (profile == null) {
            profile = UserProfile.builder().user(user).build();
            user.setProfile(profile);
        }

        if (profile.getAvatarUrl() != null) {
            cloudinaryService.delete(profile.getAvatarUrl());
        }
        String avatarUrl = cloudinaryService.upload(file, "avatars", "image");
        profile.setAvatarUrl(avatarUrl);
        userProfileRepository.save(profile);

        log.info("Avatar updated for user: {}", user.getEmail());

        return mapToResponse(user);
    }

    @Transactional
    public void changePassword(UserPrincipal principal, ChangePasswordRequest request) {

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new PasswordMismatchException();
        }

        passwordValidator.validate(request.getNewPassword());

        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(UserNotFoundException::new);

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.info("Password changed for user: {}", user.getEmail());
    }

    @Transactional(readOnly = true)
    public UserGrowthDto calculateUserGrowth(Duration period) {
        Instant now = Instant.now();

        Instant currentPeriodStart = now.minus(period);
        long currentPeriodCount = userRepository.countByRoleAndCreatedAtBetween(
                "CUSTOMER", currentPeriodStart, now);

        Instant previousPeriodStart = now.minus(period.multipliedBy(2));
        Instant previousPeriodEnd = currentPeriodStart;
        long previousPeriodCount = userRepository.countByRoleAndCreatedAtBetween(
                "CUSTOMER", previousPeriodStart, previousPeriodEnd);

        BigDecimal growthPercentage = calculateGrowthPercentage(currentPeriodCount, previousPeriodCount);

        return UserGrowthDto.builder()
                .currentPeriodCount(currentPeriodCount)
                .previousPeriodCount(previousPeriodCount)
                .growthPercentage(growthPercentage)
                .build();
    }

    private BigDecimal calculateGrowthPercentage(long current, long previous) {
        if (previous == 0) {
            return current == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(100);
        }
        return BigDecimal.valueOf(current - previous)
                .divide(BigDecimal.valueOf(previous), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ─── Admin methods ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getAllForAdmin(UserFilterRequest filter) {
        String searchTerm = SearchPatterns.like(filter.getSearch());

        Page<User> users = userRepository.findAllForAdmin(
                filter.getRole(),
                filter.getIsActive(),
                filter.getEnabled(),
                searchTerm,
                filter.toPageable());

        // Compute summary over the WHOLE dataset (not the page, not the filters)
        long total = userRepository.count();
        long admins = userRepository.countByRole("ADMIN");
        long customers = userRepository.countByRole("CUSTOMER");
        long blocked = userRepository.countByEnabledFalse();
        long unverified = 0; // will be addressed in a future phase with emailVerified column

        java.util.List<com.nocteon.nocteon_api.common.dto.Summary.RoleCount> byRole = buildByRole();

        // Build the page response with summary
        PageResponse<UserResponse> pageResponse = PageResponse.of(users.map(this::mapToResponse));

        // Create a response that includes the summary
        // For now, we'll return the page response and the summary can be
        // obtained separately or added in a future enhancement
        return pageResponse;
    }

    @Transactional(readOnly = true)
    public Summary getAdminSummary() {
        long total = userRepository.count();
        long admins = userRepository.countByRole("ADMIN");
        long customers = userRepository.countByRole("CUSTOMER");
        long blocked = userRepository.countByEnabledFalse();
        long unverified = 0;
        return Summary.builder()
                .total(total)
                .admins(admins)
                .customers(customers)
                .blocked(blocked)
                .unverified(unverified)
                .byRole(buildByRole())
                .build();
    }

    @Transactional
    public UserResponse toggleActive(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException());

        boolean nowActive = !user.isActive();
        user.setActive(nowActive);
        userRepository.save(user);

        if (nowActive) {
            auditLogService.logSuccess(AuditAction.USER_UNBLOCKED, null, null, "User", userId, null, null, null, null, null, null);
        } else {
            auditLogService.logSuccess(AuditAction.USER_BLOCKED, null, null, "User", userId, null, null, null, null, null, null);
        }
        log.info("User {} isActive toggled to {} by admin", userId, user.isActive());

        return mapToResponse(user);
    }

    @Transactional
    public UserResponse adminUpdate(Long userId, AdminUpdateUserRequest request, UserPrincipal actor) {
        User user = userRepository.findByIdWithProfile(userId)
                .orElseThrow(() -> new UserNotFoundException());

        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new UsernameAlreadyExistsException();
            }
            user.setUsername(request.getUsername());
        }

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new EmailAlreadyExistsException();
            }
            user.setEmail(request.getEmail());
        }

        if (request.getRole() != null && request.getRole() != user.getRole()) {
            // An authority check, not a role check: USER_CHANGE_ROLE is what separates "may edit
            // this account" from "may change what this account is allowed to do". The route above
            // is gated on user:update, which MODERATOR holds, so without this a moderator could
            // promote any user — including themselves — to ADMIN and inherit every permission,
            // among them user:delete and user:create, which MODERATOR deliberately lacks.
            boolean mayChangeRole = actor != null && actor.getAuthorities().stream()
                    .anyMatch(authority -> Permission.USER_CHANGE_ROLE.getPermission()
                            .equals(authority.getAuthority()));
            if (!mayChangeRole) {
                log.warn("User {} attempted to change the role of user {} to {}",
                        actor == null ? "unknown" : actor.getUserId(),
                        userId,
                        request.getRole());
                throw new RoleChangeForbiddenException();
            }
            user.setRole(request.getRole());
        }

        UserProfile profile = user.getProfile();
        if (profile == null) {
            profile = UserProfile.builder().user(user).build();
            user.setProfile(profile);
        }

        if (request.getFirstName() != null) {
            profile.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            profile.setLastName(request.getLastName());
        }

        userProfileRepository.save(profile);
        userRepository.save(user);

        log.info("Admin updated user: {}", userId);

        return mapToResponse(user);
    }

    @Transactional
    public UserResponse adminCreate(AdminRegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UsernameAlreadyExistsException();
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException();
        }

        passwordValidator.validate(request.getPassword());

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .enabled(true)
                .isActive(true)
                .build();

        UserProfile profile = UserProfile.builder()
                .user(user)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .build();

        user.setProfile(profile);

        userRepository.save(user);
        userProfileRepository.save(profile);

        log.info("Admin created user: {}", user.getEmail());

        return mapToResponse(user);
    }

    @Transactional
    public UserResponse changeRole(Long userId, UserPrincipal principal, String newRole) {
        User currentUser = userRepository.findByIdWithProfile(principal.getUserId())
                .orElseThrow();

        User user = userRepository.findByIdWithProfile(userId)
                .orElseThrow(() -> new UserNotFoundException());

        // 403: Cannot change own role
        if (userId.equals(currentUser.getId())) {
            throw new CannotChangeOwnRoleException();
        }

        // 422: Unknown role - validate the role exists
        if (newRole == null) {
            throw new UnknownRoleException(List.of("ADMIN", "MODERATOR", "CUSTOMER"));
        }

        String oldRole = user.getRole();
        if (oldRole.equals(newRole)) {
            // Same role -> 200 with no changes
            return mapToResponse(user);
        }

        // Validate role exists (system enum or roles table)
        if (!isValidRole(newRole)) {
            throw new UnknownRoleException(List.of("ADMIN", "MODERATOR", "CUSTOMER"));
        }

        // Last admin protection (with pessimistic locking)
        if ("ADMIN".equals(oldRole) && !"ADMIN".equals(newRole)) {
            // Lock admin rows and re-count inside lock
            long adminCount = userRepository.findByRoleForUpdate("ADMIN").size();
            if (adminCount <= 1) {
                throw new LastAdminRequiredException();
            }
        }

        user.setRole(newRole);
        userRepository.save(user);

        // Increment token version to invalidate existing tokens
        user.incrementTokenVersion();
        userRepository.save(user);

        // Increment auth version to invalidate stale tokens
        user.incrementAuthVersion();
        userRepository.save(user);

        // Build granted/revoked diff
        var oldPermissions = permissionsOf(oldRole);
        var newPermissions = permissionsOf(newRole);

        var granted = new java.util.ArrayList<String>();
        var revoked = new java.util.ArrayList<String>();

        for (String perm : newPermissions) {
            if (!oldPermissions.contains(perm)) {
                granted.add(perm);
            }
        }
        for (String perm : oldPermissions) {
            if (!newPermissions.contains(perm)) {
                revoked.add(perm);
            }
        }

        // sensitive granted
        java.util.List<String> sensitiveGranted = new java.util.ArrayList<>();
        for (String perm : granted) {
            if (sensitivePermissions.contains(perm)) {
                sensitiveGranted.add(perm);
            }
        }

        auditLogService.logSuccess(AuditAction.ROLE_CHANGED, principal.getUserId(), null, "User", userId, null, null, null,
                java.util.Map.of("from", oldRole),
                java.util.Map.of("to", newRole, "grantedCount", granted.size(), "revokedCount", revoked.size()),
                null);

        UserResponse response = mapToResponse(user);
        response.setGranted(granted);
        response.setRevoked(revoked);
        response.setSensitiveGranted(sensitiveGranted);
        return response;
    }

    @Transactional(readOnly = true)
    public UserActivityResponse getUserActivity(Long userId) {
        userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException());
        Object[] row = orderRepository.getActivityByUser(userId);
        long count = row[0] == null ? 0 : ((Number) row[0]).longValue();
        java.math.BigDecimal spend = row[1] == null ? java.math.BigDecimal.ZERO : new java.math.BigDecimal(row[1].toString());
        java.time.Instant last = (java.time.Instant) row[2];
        return UserActivityResponse.builder()
                .ordersCount(count)
                .lifetimeSpend(spend)
                .lastOrderAt(last)
                .build();
    }

    private boolean isValidRole(String role) {
        if (role == null) return false;
        if (roleRepository.existsById(role)) return true;
        try {
            com.nocteon.nocteon_api.auth.enums.Role.valueOf(role);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private java.util.List<String> permissionsOf(String role) {
        if (role == null) return java.util.List.of();
        if (roleRepository.existsById(role)) {
            return new java.util.ArrayList<>(roleRepository.findWithPermissionsByName(role).orElseThrow().getPermissions());
        }
        try {
            return com.nocteon.nocteon_api.auth.enums.Role.valueOf(role).getPermissions().stream()
                    .map(com.nocteon.nocteon_api.common.enums.Permission::getPermission)
                    .toList();
        } catch (IllegalArgumentException e) {
            return java.util.List.of();
        }
    }

    // ─── Private helpers ───────────────────────────────────────────

    private java.util.List<com.nocteon.nocteon_api.common.dto.Summary.RoleCount> buildByRole() {
        java.util.List<com.nocteon.nocteon_api.common.dto.Summary.RoleCount> list = new java.util.ArrayList<>();
        for (Object[] row : userRepository.countUsersGroupByRole()) {
            String role = row[0] == null ? null : row[0].toString();
            long count = row[1] instanceof Number ? ((Number) row[1]).longValue() : 0L;
            list.add(com.nocteon.nocteon_api.common.dto.Summary.RoleCount.builder()
                    .role(role)
                    .userCount(count)
                    .count(count)
                    .build());
        }
        return list;
    }

    private UserResponse mapToResponse(User user) {
        UserProfile profile = user.getProfile();

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .isActive(user.isActive())
                .firstName(profile != null ? profile.getFirstName() : null)
                .lastName(profile != null ? profile.getLastName() : null)
                .avatarUrl(profile != null ? profile.getAvatarUrl() : null)
                .createdAt(user.getCreatedAt())
                .lastActiveAt(user.getLastActiveAt())
                .permissions(permissionsOf(user.getRole()))
                .build();
    }
}