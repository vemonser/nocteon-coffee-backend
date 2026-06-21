package com.nocteon.nocteon_api.auth.oauth2;

import java.security.SecureRandom;
import java.util.Optional;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.entity.UserLinkedAccount;
import com.nocteon.nocteon_api.auth.entity.UserProfile;
import com.nocteon.nocteon_api.auth.enums.Provider;
import com.nocteon.nocteon_api.auth.enums.Role;
import com.nocteon.nocteon_api.auth.repository.UserLinkedAccountRepository;
import com.nocteon.nocteon_api.auth.repository.UserProfileRepository;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserLinkedAccountRepository userLinkedAccountRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Provider provider = Provider.valueOf(registrationId.toUpperCase());

        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(provider, oAuth2User.getAttributes());

        User user = processOAuth2User(provider, userInfo);

        return UserPrincipal.create(user, oAuth2User.getAttributes());
    }

    private User processOAuth2User(Provider provider, OAuth2UserInfo userInfo) {

        // هل فيه حساب مربوط بنفس الـ provider_id ده؟
        return userLinkedAccountRepository
                .findByProviderAndProviderId(provider, userInfo.getId())
                .map(UserLinkedAccount::getUser)
                .orElseGet(() -> handleNewOAuth2User(provider, userInfo));
    }

    private User handleNewOAuth2User(Provider provider, OAuth2UserInfo userInfo) {

        // هل فيه يوزر بنفس الإيميل (سجّل LOCAL قبل كده)؟
        Optional<User> existingUser = userRepository.findByEmail(userInfo.getEmail());

        if (existingUser.isPresent()) {
            // Account Linking — ربط الـ provider الجديد بالحساب الموجود
            return linkProviderToExistingUser(existingUser.get(), provider, userInfo);
        }

        // يوزر جديد تماماً — أنشئ حساب جديد
        return createNewOAuth2User(provider, userInfo);
    }

    private User linkProviderToExistingUser(User user, Provider provider, OAuth2UserInfo userInfo) {
        UserLinkedAccount linkedAccount = UserLinkedAccount.builder()
                .user(user)
                .provider(provider)
                .providerId(userInfo.getId())
                .build();

        userLinkedAccountRepository.save(linkedAccount);
        return user;
    }

    private User createNewOAuth2User(Provider provider, OAuth2UserInfo userInfo) {
        String username = generateUsername(userInfo.getName());

        User user = User.builder()
                .email(userInfo.getEmail())
                .username(username)
                .provider(provider)
                .providerId(userInfo.getId())
                .role(Role.CUSTOMER)
                .enabled(true)
                .build();

        user = userRepository.save(user);

        UserProfile profile = UserProfile.builder()
                .user(user)
                .firstName(userInfo.getFirstName())
                .lastName(userInfo.getLastName())
                .avatarUrl(userInfo.getAvatarUrl())
                .build();

        userProfileRepository.save(profile);

        return user;
    }

    private String generateUsername(String name) {
        String base = name.toLowerCase()
                .replaceAll("[^a-z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");

        if (!userRepository.existsByUsername(base)) {
            return base;
        }

        String candidate;
        do {
            int suffix = new SecureRandom().nextInt(9000) + 1000;
            candidate = base + "_" + suffix;
        } while (userRepository.existsByUsername(candidate));

        return candidate;
    }
}