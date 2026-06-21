package com.nocteon.nocteon_api.common.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.nocteon.nocteon_api.common.exception.email.EmailSendingException;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromAddress;

    public void sendOtpEmail(String toEmail, String otp) {
        Context context = new Context();
        context.setVariable("otp", otp);
        String html = templateEngine.process("emails/otp-email", context);
        sendHtmlEmail(toEmail, "Verify your NOCTEON Coffee account", html);
    }

    public void sendPasswordResetEmail(String toEmail, String otp) {
        Context context = new Context();
        context.setVariable("otp", otp);
        String html = templateEngine.process("emails/password-reset-email", context);
        sendHtmlEmail(toEmail, "Reset your NOCTEON Coffee password", html);
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new EmailSendingException(e);
        }
    }
}