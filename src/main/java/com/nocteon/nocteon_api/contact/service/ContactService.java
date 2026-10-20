package com.nocteon.nocteon_api.contact.service;

import java.util.Optional;

import com.nocteon.nocteon_api.mail.entity.EmailLog;
import com.nocteon.nocteon_api.mail.enums.EmailStatus;
import com.nocteon.nocteon_api.mail.enums.EmailType;
import com.nocteon.nocteon_api.mail.repository.EmailLogRepository;
import com.nocteon.nocteon_api.mail.service.TransactionalEmailService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.exception.BaseApiException;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidContactReplyException;
import com.nocteon.nocteon_api.common.exception.notFound.ContactMessageNotFoundException;
import com.nocteon.nocteon_api.contact.dto.request.ContactRequest;
import com.nocteon.nocteon_api.contact.dto.response.ContactResponse;
import com.nocteon.nocteon_api.contact.entity.ContactMessage;
import com.nocteon.nocteon_api.contact.repository.ContactMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContactService {

    private final ContactMessageRepository contactMessageRepository;
    private final TransactionalEmailService transactionalEmailService;
    private final EmailLogRepository emailLogRepository;

    @Transactional
    public ContactResponse submit(ContactRequest request, String ip) {
        ContactMessage message = ContactMessage.builder()
                .name(request.getName().trim())
                .email(request.getEmail().trim().toLowerCase())
                .subject(request.getSubject().trim())
                .message(request.getMessage().trim())
                .build();

        ContactMessage saved = contactMessageRepository.save(message);
        log.info("Contact message saved id={} from={} ip={}", saved.getId(), saved.getEmail(), ip);

        return buildResponse(saved);
    }

    public PageResponse<ContactResponse> getAll(BaseFilterRequest filter) {
        Page<ContactMessage> page = contactMessageRepository.findAll(filter.toPageable());
        return buildPage(page);
    }

    @Transactional(readOnly = true)
    public ContactResponse getById(Long id) {
        ContactMessage message = contactMessageRepository.findById(id)
                .orElseThrow(() -> new ContactMessageNotFoundException());
        return buildResponse(message);
    }

    @Transactional
    public ContactResponse markAsRead(Long id) {
        ContactMessage message = contactMessageRepository.findById(id)
                .orElseThrow(() -> new ContactMessageNotFoundException());
        message.setRead(true);
        return buildResponse(contactMessageRepository.save(message));
    }

    /**
     * Save the reply and send the customer an email.
     *
     * <p>The reply is saved FIRST and the email is attempted SECOND, so a
     * failed email never loses the admin's words. The returned status is the
     * one the email actually reached: SENT, FAILED (the worker will retry) or
     * DEAD.
     */
    @Transactional
    public ContactResponse reply(Long id, String replyContent) {
        ContactMessage message = contactMessageRepository.findById(id)
                .orElseThrow(() -> new ContactMessageNotFoundException());
        message.setRead(true);
        message.setReplied(true);
        message.setReplyContent(replyContent);
        ContactMessage saved = contactMessageRepository.save(message);

        EmailStatus emailStatus = transactionalEmailService.enqueueOrResendEmail(
                "contact-reply:" + saved.getId(),
                EmailType.CONTACT_REPLY,
                saved.getEmail(),
                "contact_message",
                saved.getId());

        return buildResponse(saved, emailStatus);
    }

    /**
     * Send the stored reply again, without changing it.
     *
     * <p>The escape hatch when the provider refused the message, or when the
     * address was wrong.
     */
    @Transactional
    public ContactResponse resendEmail(Long id) {
        ContactMessage message = contactMessageRepository.findById(id)
                .orElseThrow(() -> new ContactMessageNotFoundException());
        if (message.getReplyContent() == null || message.getReplyContent().isBlank()) {
            throw new InvalidContactReplyException();
        }

        EmailStatus emailStatus = transactionalEmailService.enqueueOrResendEmail(
                "contact-reply:" + message.getId(),
                EmailType.CONTACT_REPLY,
                message.getEmail(),
                "contact_message",
                message.getId());

        return buildResponse(message, emailStatus);
    }

    @Transactional
    public void delete(Long id) {
        ContactMessage message = contactMessageRepository.findById(id)
                .orElseThrow(() -> new ContactMessageNotFoundException());
        contactMessageRepository.delete(message);
    }

    public PageResponse<ContactResponse> getAllUnread(BaseFilterRequest filter) {
        Page<ContactMessage> page = contactMessageRepository.findByIsReadFalse(filter.toPageable());
        return buildPage(page);
    }

    private ContactResponse buildResponse(ContactMessage message) {
        return buildResponse(message, emailStatusFor(message.getId()));
    }

    private ContactResponse buildResponse(ContactMessage message, EmailStatus status) {
        return ContactResponse.builder()
                .id(message.getId())
                .name(message.getName())
                .email(message.getEmail())
                .subject(message.getSubject())
                .message(message.getMessage())
                .isRead(message.isRead())
                .isReplied(message.isReplied())
                .replyContent(message.getReplyContent())
                .emailStatus(status == null ? "NONE" : status.name())
                .createdAt(message.getCreatedAt())
                .build();
    }

    /**
     * The recorded state of this message's email, or NONE when it was never
     * emailed.
     */
    private EmailStatus emailStatusFor(Long messageId) {
        if (messageId == null) {
            return null;
        }
        return emailLogRepository
                .findByRelatedEntityTypeAndRelatedEntityIdIn("contact_message", List.of(messageId))
                .stream()
                .findFirst()
                .map(EmailLog::getStatus)
                .orElse(null);
    }

    /**
     * The page, with the email status of every message read in ONE query —
     * twenty messages must not cost twenty round trips.
     */
    private PageResponse<ContactResponse> buildPage(Page<ContactMessage> page) {
        List<ContactMessage> content = page.getContent();
        Map<Long, EmailStatus> statuses = emailStatusesFor(content);

        List<ContactResponse> responses = content.stream()
                .map(m -> buildResponse(m, statuses.get(m.getId())))
                .toList();

        return PageResponse.<ContactResponse>builder()
                .content(responses)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    private Map<Long, EmailStatus> emailStatusesFor(List<ContactMessage> messages) {
        if (messages.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = messages.stream().map(ContactMessage::getId).toList();
        Map<Long, EmailStatus> statuses = new HashMap<>();
        for (EmailLog log : emailLogRepository
                .findByRelatedEntityTypeAndRelatedEntityIdIn("contact_message", ids)) {
            statuses.putIfAbsent(log.getRelatedEntityId(), log.getStatus());
        }
        return statuses;
    }
}
