package com.nocteon.nocteon_api.auth.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.auth.dto.request.VerifyEmailRequest;
import com.nocteon.nocteon_api.auth.dto.response.EmailStatusResponse;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.entity.VerificationCode;
import com.nocteon.nocteon_api.auth.enums.VerificationType;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.repository.VerificationCodeRepository;
import com.nocteon.nocteon_api.common.email.EmailService;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidVerificationException;
import com.nocteon.nocteon_api.common.exception.notFound.UserNotFoundException;
import com.nocteon.nocteon_api.common.util.OtpGenerator;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailVerificationService {

    private final VerificationCodeRepository verificationCodeRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${application.security.verification.token-expiry-minutes:10}")
    private int expiryMinutes;

    @Transactional
    public void sendVerificationEmail(User user) {

        verificationCodeRepository
                .findByUserIdAndTypeAndUsed(user.getId(), VerificationType.EMAIL_VERIFICATION, false)
                .ifPresent(verificationCodeRepository::delete);

        String otp = OtpGenerator.generate();

        VerificationCode code = VerificationCode.builder()
                .user(user)
                .codeHashed(passwordEncoder.encode(otp))
                .type(VerificationType.EMAIL_VERIFICATION)
                .expiresAt(Instant.now().plus(expiryMinutes, ChronoUnit.MINUTES))
                .used(false)
                .build();

        verificationCodeRepository.save(code);

        emailService.sendOtpEmail(user.getEmail(), otp);

        log.info("Verification OTP sent to {}", user.getEmail());
    }

    @Transactional
    public void resendVerificationEmail(String email) {
        // Deliberately silent for unknown/already-verified emails: this endpoint
        // must not become an account-existence oracle.
        userRepository.findByEmail(normalizeEmail(email))
                .filter(user -> !user.isEnabled())
                .ifPresent(this::sendVerificationEmail);
    }

    public EmailStatusResponse checkEmailStatus(String email) {
        return userRepository.findByEmail(normalizeEmail(email))
                .map(user -> EmailStatusResponse.builder()
                        .exists(true)
                        .verified(user.isEnabled())
                        .build())
                .orElse(EmailStatusResponse.builder()
                        .exists(false)
                        .verified(false)
                        .build());
    }

    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.getEmail()))
                .orElseThrow(UserNotFoundException::new);

        VerificationCode code = verificationCodeRepository
                .findByUserIdAndTypeAndUsed(user.getId(), VerificationType.EMAIL_VERIFICATION, false)
                .orElseThrow(InvalidVerificationException::new);

        if (code.getExpiresAt().isBefore(Instant.now())) {
            verificationCodeRepository.delete(code);
            throw new InvalidVerificationException();
        }

        if (!passwordEncoder.matches(request.getOtp(), code.getCodeHashed())) {
            throw new InvalidVerificationException();
        }

        user.setEnabled(true);
        userRepository.save(user);

        verificationCodeRepository.delete(code);

        log.info("Email verified successfully for {}", user.getEmail());
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}