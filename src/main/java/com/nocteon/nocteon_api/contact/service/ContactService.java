package com.nocteon.nocteon_api.contact.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.exception.BaseApiException;
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
        return PageResponse.of(page, this::buildResponse);
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

    @Transactional
    public ContactResponse reply(Long id, String replyContent) {
        ContactMessage message = contactMessageRepository.findById(id)
                .orElseThrow(() -> new ContactMessageNotFoundException());
        message.setRead(true);
        message.setReplied(true);
        message.setReplyContent(replyContent);
        return buildResponse(contactMessageRepository.save(message));
    }

    @Transactional
    public void delete(Long id) {
        ContactMessage message = contactMessageRepository.findById(id)
                .orElseThrow(() -> new ContactMessageNotFoundException());
        contactMessageRepository.delete(message);
    }

    public PageResponse<ContactResponse> getAllUnread(BaseFilterRequest filter) {
        Page<ContactMessage> page = contactMessageRepository.findByIsReadFalse(filter.toPageable());
        return PageResponse.of(page, this::buildResponse);
    }

    private ContactResponse buildResponse(ContactMessage message) {
        return ContactResponse.builder()
                .id(message.getId())
                .name(message.getName())
                .email(message.getEmail())
                .subject(message.getSubject())
                .message(message.getMessage())
                .isRead(message.isRead())
                .isReplied(message.isReplied())
                .replyContent(message.getReplyContent())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
