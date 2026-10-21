package com.nocteon.nocteon_api.contact.service;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.nocteon.nocteon_api.mail.repository.EmailLogRepository;
import com.nocteon.nocteon_api.mail.service.TransactionalEmailService;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.contact.dto.request.ContactRequest;
import com.nocteon.nocteon_api.contact.dto.response.ContactResponse;
import com.nocteon.nocteon_api.contact.entity.ContactMessage;
import com.nocteon.nocteon_api.contact.repository.ContactMessageRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock
    private ContactMessageRepository contactMessageRepository;

    @Mock
    private TransactionalEmailService transactionalEmailService;

    @Mock
    private EmailLogRepository emailLogRepository;

    @InjectMocks
    private ContactService contactService;

    @Test
    void submit_savesTrimmedMessage() {
        ContactMessage saved = ContactMessage.builder()
                .id(1L)
                .name("Ahmed Ali")
                .email("ahmed@example.com")
                .subject("Question about beans")
                .message("Hello, I have a question about your espresso beans.")
                .build();
        when(contactMessageRepository.save(any(ContactMessage.class))).thenReturn(saved);

        ContactRequest request = new ContactRequest();
        request.setName("  Ahmed Ali  ");
        request.setEmail(" AHMED@example.com ");
        request.setSubject("  Question about beans  ");
        request.setMessage("  Hello, I have a question about your espresso beans.  ");

        ContactResponse response = contactService.submit(request, "127.0.0.1");

        ArgumentCaptor<ContactMessage> captor = ArgumentCaptor.forClass(ContactMessage.class);
        verify(contactMessageRepository).save(captor.capture());

        ContactMessage message = captor.getValue();
        assertThat(message.getName()).isEqualTo("Ahmed Ali");
        assertThat(message.getEmail()).isEqualTo("ahmed@example.com");
        assertThat(message.getSubject()).isEqualTo("Question about beans");
        assertThat(message.getMessage()).isEqualTo("Hello, I have a question about your espresso beans.");

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("ahmed@example.com");
    }

    @Test
    void getAll_mapsEntitiesToResponses() {
        ContactMessage message = ContactMessage.builder()
                .id(1L)
                .name("Ahmed Ali")
                .email("ahmed@example.com")
                .subject("Question")
                .message("Hello, this is a test message body.")
                .build();
        Page<ContactMessage> page = new PageImpl<>(List.of(message));

        when(contactMessageRepository.findAll(any(Pageable.class))).thenReturn(page);

        PageResponse<ContactResponse> result = contactService.getAll(new BaseFilterRequest());

        assertThat(result.getContent()).hasSize(1);
        ContactResponse response = result.getContent().get(0);
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Ahmed Ali");
        assertThat(response.getEmail()).isEqualTo("ahmed@example.com");
        assertThat(response.getSubject()).isEqualTo("Question");
        assertThat(response.getMessage()).isEqualTo("Hello, this is a test message body.");
    }
}