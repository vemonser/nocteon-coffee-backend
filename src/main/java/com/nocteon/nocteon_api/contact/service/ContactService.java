package com.nocteon.nocteon_api.contact.service;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
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

    private ContactResponse buildResponse(ContactMessage message) {
        return ContactResponse.builder()
                .id(message.getId())
                .name(message.getName())
                .email(message.getEmail())
                .subject(message.getSubject())
                .message(message.getMessage())
                .createdAt(message.getCreatedAt())
                .build();
    }
}