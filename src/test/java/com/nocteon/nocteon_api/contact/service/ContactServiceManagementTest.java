package com.nocteon.nocteon_api.contact.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;
import com.nocteon.nocteon_api.common.exception.notFound.ContactMessageNotFoundException;

import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.contact.dto.response.ContactResponse;
import com.nocteon.nocteon_api.contact.entity.ContactMessage;
import com.nocteon.nocteon_api.contact.repository.ContactMessageRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ContactServiceManagementTest {

    @Mock
    private ContactMessageRepository contactMessageRepository;

    @InjectMocks
    private ContactService contactService;

    @Test
    void getById_existingMessage_returnsMessage() {
        ContactMessage message = ContactMessage.builder()
                .id(1L)
                .name("Ahmed Ali")
                .email("ahmed@example.com")
                .subject("Question")
                .message("Hello, I have a question.")
                .isRead(false)
                .build();
        when(contactMessageRepository.findById(1L)).thenReturn(Optional.of(message));

        ContactResponse response = contactService.getById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Ahmed Ali");
        assertThat(response.isRead()).isFalse();
    }

    @Test
    void getById_nonExistentMessage_throwsException() {
        when(contactMessageRepository.findById(999L)).thenReturn(Optional.empty());

        // Asserted on the type and status rather than the message: BaseApiException carries a
        // message *key* that GlobalExceptionHandler resolves through MessageSource at render
        // time, so the exception's own text is a key and not an English sentence.
        assertThatThrownBy(() -> contactService.getById(999L))
                .isInstanceOf(ContactMessageNotFoundException.class)
                .isInstanceOf(BaseApiException.class)
                .extracting(e -> ((BaseApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void markAsRead_setsReadTrueAndSaves() {
        ContactMessage message = ContactMessage.builder()
                .id(1L)
                .name("Ahmed Ali")
                .email("ahmed@example.com")
                .subject("Question")
                .message("Hello")
                .isRead(false)
                .build();
        when(contactMessageRepository.findById(1L)).thenReturn(Optional.of(message));
        // A JPA save returns the managed entity; an unstubbed mock returns null, and the service
        // feeds that return value straight into buildResponse.
        when(contactMessageRepository.save(any(ContactMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ContactResponse response = contactService.markAsRead(1L);

        assertThat(response.isRead()).isTrue();
        verify(contactMessageRepository).save(message);
    }

    @Test
    void reply_setsRepliedFieldsAndSaves() {
        ContactMessage message = ContactMessage.builder()
                .id(1L)
                .name("Ahmed Ali")
                .email("ahmed@example.com")
                .subject("Question")
                .message("Hello")
                .isRead(true)
                .build();
        when(contactMessageRepository.findById(1L)).thenReturn(Optional.of(message));
        when(contactMessageRepository.save(any(ContactMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ContactResponse response = contactService.reply(1L, "Thanks for reaching out!");

        assertThat(response.isReplied()).isTrue();
        assertThat(response.getReplyContent()).isEqualTo("Thanks for reaching out!");
        verify(contactMessageRepository).save(message);
    }

    @Test
    void delete_existingMessage_deletesAndReturnsVoid() {
        ContactMessage message = ContactMessage.builder()
                .id(1L)
                .name("Ahmed Ali")
                .email("ahmed@example.com")
                .subject("Question")
                .message("Hello")
                .build();
        when(contactMessageRepository.findById(1L)).thenReturn(Optional.of(message));

        contactService.delete(1L);

        verify(contactMessageRepository).delete(message);
    }

    @Test
    void getAll_withUnreadOnly_returnsFilteredMessages() {
        ContactMessage unread = ContactMessage.builder()
                .id(1L)
                .name("Ahmed Ali")
                .email("ahmed@example.com")
                .subject("Question")
                .message("Hello")
                .isRead(false)
                .build();
        Page<ContactMessage> page = new PageImpl<>(List.of(unread));
        when(contactMessageRepository.findByIsReadFalse(any(Pageable.class))).thenReturn(page);

        PageResponse<ContactResponse> result = contactService.getAllUnread(new BaseFilterRequest());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).isRead()).isFalse();
    }
}
