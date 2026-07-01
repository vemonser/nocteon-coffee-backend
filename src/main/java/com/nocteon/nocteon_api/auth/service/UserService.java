package com.nocteon.nocteon_api.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.auth.dto.request.ChangePasswordRequest;
import com.nocteon.nocteon_api.auth.dto.request.UpdateProfileRequest;
import com.nocteon.nocteon_api.auth.dto.response.UserResponse;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.entity.UserProfile;
import com.nocteon.nocteon_api.auth.repository.UserProfileRepository;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.cloudinary.service.CloudinaryService;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidCredentialsException;
import com.nocteon.nocteon_api.common.exception.notFound.UserNotFoundException;
import com.nocteon.nocteon_api.common.exception.user.PasswordMismatchException;
import com.nocteon.nocteon_api.common.util.PasswordValidator;

import jakarta.transaction.Transactional;
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

    public UserResponse getMe(UserPrincipal principal) {
        User user = userRepository.findByIdWithProfile(principal.getUserId())
                .orElseThrow(UserNotFoundException::new);
        UserProfile profile = user.getProfile();
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .role(user.getRole())
                .firstName(profile != null ? profile.getFirstName() : null)
                .lastName(profile != null ? profile.getLastName() : null)
                .avatarUrl(profile != null ? profile.getAvatarUrl() : null)
                .build();
    }

    @Transactional
    public UserResponse updateProfile(UserPrincipal principal, UpdateProfileRequest request) {
        User user = userRepository.findByIdWithProfile(principal.getUserId())
                .orElseThrow(UserNotFoundException::new);

        UserProfile profile = user.getProfile();

        if (profile == null) {
            profile = UserProfile.builder().user(user).build();
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

        return getMe(principal);
    }

    @Transactional
    public UserResponse updateAvatar(UserPrincipal principal, MultipartFile file) {
        User user = userRepository.findByIdWithProfile(principal.getUserId())
                .orElseThrow(UserNotFoundException::new);

        UserProfile profile = user.getProfile();

        if (profile == null) {
            profile = UserProfile.builder().user(user).build();
        }

        if (profile.getAvatarUrl() != null) {
            cloudinaryService.delete(profile.getAvatarUrl());
        }
        String avatarUrl = cloudinaryService.upload(file, "avatars","image");
        profile.setAvatarUrl(avatarUrl);
        userProfileRepository.save(profile);

        log.info("Avatar updated for user: {}", user.getEmail());

        return getMe(principal);
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
}
