package com.nocteon.nocteon_api.auth.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.auth.entity.UserLinkedAccount;
import com.nocteon.nocteon_api.auth.enums.Provider;

public interface UserLinkedAccountRepository extends
        JpaRepository<UserLinkedAccount, Long> {
    List<UserLinkedAccount> findByUserId(Long userId);

    Optional<UserLinkedAccount> findByProviderAndProviderId(Provider provider, String providerId);

    boolean existsByUserIdAndProvider(Long userId, Provider provider);

}
