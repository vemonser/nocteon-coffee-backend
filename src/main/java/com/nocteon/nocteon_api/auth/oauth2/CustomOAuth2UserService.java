package com.nocteon.nocteon_api.auth.oauth2;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.entity.UserLinkedAccount;
import com.nocteon.nocteon_api.auth.entity.UserProfile;
import com.nocteon.nocteon_api.auth.enums.Provider;
import com.nocteon.nocteon_api.auth.repository.UserLinkedAccountRepository;
import com.nocteon.nocteon_api.auth.repository.UserProfileRepository;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserLinkedAccountRepository userLinkedAccountRepository;
    private final GithubEmailVerifier githubEmailVerifier;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Provider provider = Provider.valueOf(registrationId.toUpperCase());
        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(
                provider, oAuth2User.getAttributes());

        Optional<UserLinkedAccount> linked =
                userLinkedAccountRepository.findByProviderAndProviderId(provider, userInfo.getId());

        User user;
        if (linked.isPresent()) {
            // An already-linked provider id is trusted; no email API call is needed
            // on every login (and a provider outage cannot lock the user out).
            user = linked.get().getUser();
        } else {
            String email = resolveVerifiedEmail(provider, userInfo,
                    userRequest.getAccessToken().getTokenValue());
            user = linkOrCreate(provider, userInfo, email);
        }

        if (!user.isEnabled() || !user.isActive() || user.isLocked()) {
            throw new OAuth2AuthenticationException("account_disabled");
        }

        return UserPrincipal.create(user, oAuth2User.getAttributes());
    }

    /**
     * Never link or create an account from an email the provider has not
     * verified. Google exposes email_verified; GitHub needs /user/emails.
     */
    private String resolveVerifiedEmail(Provider provider, OAuth2UserInfo userInfo, String accessToken) {
        String email;
        if (provider == Provider.GITHUB) {
            email = githubEmailVerifier.verifiedEmail(accessToken)
                    .orElseThrow(() -> new OAuth2AuthenticationException("email_not_verified"));
        } else {
            if (!userInfo.isEmailVerified() || userInfo.getEmail() == null
                    || userInfo.getEmail().isBlank()) {
                throw new OAuth2AuthenticationException("email_not_verified");
            }
            email = userInfo.getEmail();
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private User linkOrCreate(Provider provider, OAuth2UserInfo userInfo, String email) {
        return userRepository.findByEmail(email)
                .map(existing -> linkProviderToExistingUser(existing, provider, userInfo))
                .orElseGet(() -> {
                    try {
                        return createNewOAuth2User(provider, userInfo, email);
                    } catch (DataIntegrityViolationException e) {
                        // A concurrent callback created the user/link after our read;
                        // resolve the winner instead of returning a 500.
                        return userLinkedAccountRepository
                                .findByProviderAndProviderId(provider, userInfo.getId())
                                .map(UserLinkedAccount::getUser)
                                .orElseGet(() -> userRepository.findByEmail(email)
                                        .map(existing -> linkProviderToExistingUser(
                                                existing, provider, userInfo))
                                        .orElseThrow(() -> new OAuth2AuthenticationException(
                                                "account_conflict")));
                    }
                });
    }

    private User linkProviderToExistingUser(User user, Provider provider, OAuth2UserInfo userInfo) {
        if (userLinkedAccountRepository.existsByUserIdAndProvider(user.getId(), provider)) {
            throw new OAuth2AuthenticationException("provider_already_linked");
        }
        UserLinkedAccount linkedAccount = UserLinkedAccount.builder()
                .user(user)
                .provider(provider)
                .providerId(userInfo.getId())
                .build();
        try {
            userLinkedAccountRepository.save(linkedAccount);
        } catch (DataIntegrityViolationException e) {
            return userLinkedAccountRepository.findByProviderAndProviderId(provider, userInfo.getId())
                    .map(UserLinkedAccount::getUser)
                    .orElseThrow(() -> new OAuth2AuthenticationException("account_conflict"));
        }
        return user;
    }

    private User createNewOAuth2User(Provider provider, OAuth2UserInfo userInfo, String email) {
        String username = generateUsername(userInfo.getName());

        User user = User.builder()
                .email(email)
                .username(username)
                .provider(provider)
                .providerId(userInfo.getId())
                .role("CUSTOMER")
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
        String base = name == null ? "user" : name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (base.isBlank()) {
            base = "user";
        }

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
