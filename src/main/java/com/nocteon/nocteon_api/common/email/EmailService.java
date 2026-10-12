package com.nocteon.nocteon_api.common.email;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.nocteon.nocteon_api.mail.entity.EmailLog;
import com.nocteon.nocteon_api.mail.enums.EmailStatus;
import com.nocteon.nocteon_api.mail.enums.EmailType;
import com.nocteon.nocteon_api.mail.repository.EmailLogRepository;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final EmailLogRepository emailLogRepository;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Async("emailTaskExecutor")
    public void sendOtpEmail(String toEmail, String otp) {
        Context context = new Context();
        context.setVariable("otp", otp);
        String html = templateEngine.process("emails/otp-email", context);
        sendHtmlEmail(toEmail, "Verify your NOCTEON Coffee account", html,
                EmailType.VERIFICATION, "user", null);
    }

    @Async("emailTaskExecutor")
    public void sendPasswordResetEmail(String toEmail, String otp) {
        Context context = new Context();
        context.setVariable("otp", otp);
        String html = templateEngine.process("emails/password-reset-email", context);
        sendHtmlEmail(toEmail, "Reset your NOCTEON Coffee password", html,
                EmailType.PASSWORD_RESET, "user", null);
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent,
            EmailType type, String relatedEntityType, Long relatedEntityId) {
        String idempotencyKey = UUID.randomUUID().toString();

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);

            log.info("Email sent successfully type={} to={}", type, to);

        } catch (MessagingException e) {
            log.error("Failed to send email type={} to={}: {}", type, to, e.getMessage());
            saveFailedEmail(idempotencyKey, type, to, relatedEntityType, relatedEntityId, e.getMessage());
        }
    }

    private void saveFailedEmail(String idempotencyKey, EmailType type, String to,
            String relatedEntityType, Long relatedEntityId, String errorMessage) {
        try {
            EmailLog logEntry = EmailLog.builder()
                    .idempotencyKey(idempotencyKey)
                    .emailType(type)
                    .recipientEmail(to)
                    .relatedEntityType(relatedEntityType)
                    .relatedEntityId(relatedEntityId)
                    .status(EmailStatus.FAILED)
                    .attemptCount(1)
                    .lastError(errorMessage)
                    .build();

            emailLogRepository.save(logEntry);
            log.info("Failed email saved for retry key={}", idempotencyKey);
        } catch (Exception ex) {
            log.error("Failed to save failed email log: {}", ex.getMessage());
        }
    }
}
