package com.nocteon.nocteon_api.auth.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.auth.dto.request.ForgotPasswordRequest;
import com.nocteon.nocteon_api.auth.dto.request.ResetPasswordRequest;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.entity.VerificationCode;
import com.nocteon.nocteon_api.auth.enums.VerificationType;
import com.nocteon.nocteon_api.auth.repository.RefreshTokenRepository;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.repository.VerificationCodeRepository;
import com.nocteon.nocteon_api.common.email.EmailService;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidVerificationException;
import com.nocteon.nocteon_api.common.exception.user.PasswordMismatchException;
import com.nocteon.nocteon_api.common.util.OtpGenerator;
import com.nocteon.nocteon_api.common.util.PasswordValidator;


import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final PasswordValidator passwordValidator;

    @Value("${application.security.reset.token-expiry-minutes:10}")
    private int expiryMinutes;

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.getEmail())
                .ifPresent(this::sendPasswordResetCode);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new PasswordMismatchException();
        }

        passwordValidator.validate(request.getNewPassword());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(InvalidVerificationException::new);

        VerificationCode code = verificationCodeRepository
                .findByUserIdAndTypeAndUsed(user.getId(), VerificationType.PASSWORD_RESET, false)
                .orElseThrow(InvalidVerificationException::new);

        if (code.getExpiresAt().isBefore(Instant.now())) {
            verificationCodeRepository.delete(code);
            throw new InvalidVerificationException();
        }

        if (!passwordEncoder.matches(request.getOtp(), code.getCodeHashed())) {
            throw new InvalidVerificationException();
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        verificationCodeRepository.delete(code);
        refreshTokenRepository.deleteByUserId(user.getId());
    }

    private void sendPasswordResetCode(User user) {
        verificationCodeRepository
                .findByUserIdAndTypeAndUsed(user.getId(), VerificationType.PASSWORD_RESET, false)
                .ifPresent(verificationCodeRepository::delete);

        String otp = OtpGenerator.generate();

        VerificationCode code = VerificationCode.builder()
                .user(user)
                .codeHashed(passwordEncoder.encode(otp))
                .type(VerificationType.PASSWORD_RESET)
                .expiresAt(Instant.now().plus(expiryMinutes, ChronoUnit.MINUTES))
                .used(false)
                .build();

        verificationCodeRepository.save(code);

        emailService.sendPasswordResetEmail(user.getEmail(), otp);
    }

}