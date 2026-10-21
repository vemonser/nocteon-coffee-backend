package com.nocteon.nocteon_api.mail.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.mail.dataProvider.EmailTemplateDataResolver;
import com.nocteon.nocteon_api.mail.entity.EmailLog;
import com.nocteon.nocteon_api.mail.enums.EmailStatus;
import com.nocteon.nocteon_api.mail.enums.EmailType;
import com.nocteon.nocteon_api.mail.exception.BrevoApiException;
import com.nocteon.nocteon_api.mail.exception.BrevoRetryableException;
import com.nocteon.nocteon_api.mail.repository.EmailLogRepository;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionalEmailService  {
    private final EmailLogRepository emailLogRepository;
    private final BrevoClient brevoClient;
    private final EmailRetryPolicy retryPolicy;
    private final EmailTemplateDataResolver templateDataResolver;
    private final ContactReplyEmailRenderer contactReplyEmailRenderer;

    @Transactional
    public void enqueueEmail(String idempotencyKey, EmailType type, String recipient,
            String relatedEntityType, Long relatedEntityId) { 
        if (emailLogRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            log.info("Email with key {} already enqueued/sent, skipping", idempotencyKey);
            return;
        }

        EmailLog logEntry = new EmailLog();
        logEntry.setIdempotencyKey(idempotencyKey);
        logEntry.setEmailType(type);
        logEntry.setRecipientEmail(recipient);
        logEntry.setRelatedEntityType(relatedEntityType);
        logEntry.setRelatedEntityId(relatedEntityId);
        logEntry.setStatus(EmailStatus.PENDING);

        try {
            emailLogRepository.saveAndFlush(logEntry);
        } catch (DataIntegrityViolationException e) {
            log.info("Race condition caught for key {}", idempotencyKey);
            return;
        }

        Map<String, Object> resolvedParams = templateDataResolver.resolve(type, relatedEntityId);
        attemptSend(logEntry, resolvedParams);
    }

    @Transactional
    public void retryPendingEmails() {
        List<EmailLog> candidates = emailLogRepository.findRetryCandidates(PageRequest.of(0, 50));

        for (EmailLog logEntry : candidates) {
            if (!retryPolicy.isDueForRetry(logEntry.getAttemptCount(), logEntry.getUpdatedAt())) {
                continue;
            }
            attemptSend(logEntry, rebuildTemplateParams(logEntry));
        }
    }

    private void attemptSend(EmailLog logEntry, Map<String, Object> templateParams) {
        try {
            String messageId;
            if (logEntry.getEmailType() == EmailType.CONTACT_REPLY) {
                // The body of a reply lives in this repository, not in a
                // provider-side template, so it is rendered here and sent as
                // raw content.
                messageId = brevoClient.sendRawTransactionalEmail(
                        logEntry.getRecipientEmail(),
                        contactReplyEmailRenderer.subject(templateParams),
                        contactReplyEmailRenderer.render(templateParams));
            } else {
                messageId = brevoClient.sendTransactionalEmail(
                        logEntry.getRecipientEmail(),
                        logEntry.getEmailType(),
                        templateParams);
            }

            logEntry.setStatus(EmailStatus.SENT);
            logEntry.setProviderMessageId(messageId);
            logEntry.setSentAt(Instant.now());
            emailLogRepository.save(logEntry);

        } catch (BrevoApiException ex) {
            int newAttemptCount = logEntry.getAttemptCount() + 1;
            logEntry.setAttemptCount(newAttemptCount);
            logEntry.setLastError(truncate(ex.getMessage(), 1000));

            boolean shouldRetry = ex instanceof BrevoRetryableException && !retryPolicy.isDead(newAttemptCount);
            logEntry.setStatus(shouldRetry ? EmailStatus.FAILED : EmailStatus.DEAD);
            emailLogRepository.save(logEntry);

            log.error("Failed to send email key={}, attempt={}", logEntry.getIdempotencyKey(), newAttemptCount, ex);
        }
    }

    private Map<String, Object> rebuildTemplateParams(EmailLog logEntry) {
        return templateDataResolver.resolve(logEntry.getEmailType(), logEntry.getRelatedEntityId());
    }

    private String truncate(String s, int max) {
        if (s == null)
            return null;
        return s.length() > max ? s.substring(0, max) : s;
    }


    /**
     * Enqueue an email, or re-send one that was already queued.
     *
     * <p>ONE row per key: the row is reset to PENDING, the attempt counter
     * is zeroed, and the send is attempted immediately. A second reply to the
     * same message therefore re-sends the CURRENT answer instead of queueing
     * a second copy of an older one.
     *
     * <p>The attempt is INLINE, so the caller learns right away whether the
     * provider accepted the message. When it did not, the row stays FAILED
     * with the error recorded, and the retry worker picks it up on the
     * backoff schedule.
     *
     * @return the status the row ended in
     */
    @Transactional
    public EmailStatus enqueueOrResendEmail(String idempotencyKey, EmailType type,
            String recipient, String relatedEntityType, Long relatedEntityId) {
        EmailLog logEntry = emailLogRepository.findByIdempotencyKey(idempotencyKey)
                .orElseGet(EmailLog::new);

        logEntry.setIdempotencyKey(idempotencyKey);
        logEntry.setEmailType(type);
        logEntry.setRecipientEmail(recipient);
        logEntry.setRelatedEntityType(relatedEntityType);
        logEntry.setRelatedEntityId(relatedEntityId);
        logEntry.setStatus(EmailStatus.PENDING);
        logEntry.setAttemptCount(0);
        logEntry.setLastError(null);
        logEntry.setSentAt(null);

        try {
            emailLogRepository.saveAndFlush(logEntry);
        } catch (DataIntegrityViolationException e) {
            log.info("Race condition caught for key {}", idempotencyKey);
            return EmailStatus.PENDING;
        }

        Map<String, Object> resolvedParams = templateDataResolver.resolve(type, relatedEntityId);
        attemptSend(logEntry, resolvedParams);
        return logEntry.getStatus();
    }
}
