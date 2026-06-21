package com.nocteon.nocteon_api.auth.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.auth.entity.VerificationCode;
import com.nocteon.nocteon_api.auth.enums.VerificationType;

public interface VerificationCodeRepository extends
        JpaRepository<VerificationCode, Long> {
    Optional<VerificationCode> findByUserIdAndTypeAndUsed(Long userId, VerificationType type, boolean used);

    void deleteByExpiresAtBefore(Instant now);

    

}
