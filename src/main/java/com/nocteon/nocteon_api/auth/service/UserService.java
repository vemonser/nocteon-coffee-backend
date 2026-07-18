package com.nocteon.nocteon_api.auth.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.auth.dto.request.AdminRegisterRequest;
import com.nocteon.nocteon_api.auth.dto.request.AdminUpdateUserRequest;
import com.nocteon.nocteon_api.auth.dto.request.ChangePasswordRequest;
import com.nocteon.nocteon_api.auth.dto.request.UpdateProfileRequest;
import com.nocteon.nocteon_api.auth.dto.request.UserFilterRequest;
import com.nocteon.nocteon_api.auth.dto.response.UserResponse;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.entity.UserProfile;
import com.nocteon.nocteon_api.auth.enums.Role;
import com.nocteon.nocteon_api.auth.repository.UserProfileRepository;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.cloudinary.service.CloudinaryService;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.enums.Permission;
import com.nocteon.nocteon_api.common.exception.email.EmailAlreadyExistsException;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidCredentialsException;
import com.nocteon.nocteon_api.common.exception.notFound.UserNotFoundException;
import com.nocteon.nocteon_api.common.exception.user.PasswordMismatchException;
import com.nocteon.nocteon_api.common.exception.user.UsernameAlreadyExistsException;
import com.nocteon.nocteon_api.common.util.PasswordValidator;
import com.nocteon.nocteon_api.dashboard.dto.UserGrowthDto;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final CloudinaryService cloudinaryService;
    private final PasswordValidator passwordValidator;

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
                Role.CUSTOMER, currentPeriodStart, now);

        Instant previousPeriodStart = now.minus(period.multipliedBy(2));
        Instant previousPeriodEnd = currentPeriodStart;
        long previousPeriodCount = userRepository.countByRoleAndCreatedAtBetween(
                Role.CUSTOMER, previousPeriodStart, previousPeriodEnd);

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
        String searchTerm = filter.getSearch() != null ? filter.getSearch() : "";

        Page<User> users = userRepository.findAllForAdmin(
                filter.getRole(),
                filter.getIsActive(),
                filter.getEnabled(),
                searchTerm,
                filter.toPageable());

        return PageResponse.of(users.map(this::mapToResponse));
    }

    @Transactional
    public UserResponse toggleActive(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException());

        user.setActive(!user.isActive());
        userRepository.save(user);

        log.info("User {} isActive toggled to {} by admin", userId, user.isActive());

        return mapToResponse(user);
    }

    @Transactional
    public UserResponse adminUpdate(Long userId, AdminUpdateUserRequest request) {
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

        if (request.getRole() != null) {
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

    // ─── Private helpers ───────────────────────────────────────────

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
                .permissions(user.getRole().getPermissions().stream()
                        .map(Permission::getPermission)
                        .toList())
                .build();
    }
}