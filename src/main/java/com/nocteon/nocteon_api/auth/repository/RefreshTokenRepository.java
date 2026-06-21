package com.nocteon.nocteon_api.auth.repository;

import com.nocteon.nocteon_api.auth.entity.RefreshToken;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByUserId(Long userId);

    void deleteByTokenHash(String tokenHash);

    void deleteByExpiresAtBefore(Instant now);
}
