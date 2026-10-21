package com.nocteon.nocteon_api.mail.dataProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nocteon.nocteon_api.contact.entity.ContactMessage;
import com.nocteon.nocteon_api.contact.repository.ContactMessageRepository;
import com.nocteon.nocteon_api.mail.enums.EmailType;

@ExtendWith(MockitoExtension.class)
class ContactReplyDataProviderTest {

    @Mock
    private ContactMessageRepository contactMessageRepository;

    @InjectMocks
    private ContactReplyDataProvider provider;

    @Test
    void theParamsCarryTheNameTheSubjectTheOriginalAndTheReply() {
        when(contactMessageRepository.findById(7L)).thenReturn(Optional.of(
                ContactMessage.builder()
                        .name("Ahmed Hassan")
                        .email("ahmed@example.com")
                        .subject("Question about shipping")
                        .message("What are the rates to Alexandria?")
                        .replyContent("Here are the rates.")
                        .build()));

        Map<String, Object> params = provider.buildParams(7L);

        assertThat(params).containsEntry("recipientName", "Ahmed Hassan");
        assertThat(params).containsEntry("originalSubject", "Question about shipping");
        assertThat(params).containsEntry("originalMessage", "What are the rates to Alexandria?");
        assertThat(params).containsEntry("replyContent", "Here are the rates.");
    }

    /**
     * A message that has never been answered has a null reply. The params map
     * must survive that: Map.of() rejects a null value, and the failure would
     * surface as a NullPointerException in the middle of a send.
     */
    @Test
    void aMessageWithNoReplyYetDoesNotBreakTheParams() {
        when(contactMessageRepository.findById(8L)).thenReturn(Optional.of(
                ContactMessage.builder()
                        .name("Fatima Ali")
                        .email("fatima@example.com")
                        .subject("Product inquiry")
                        .message("Tell me about the Ethiopia.")
                        .replyContent(null)
                        .build()));

        Map<String, Object> params = provider.buildParams(8L);

        assertThat(params).containsKey("replyContent");
        assertThat(params.get("replyContent")).isNull();
    }

    @Test
    void theProviderHandlesOnlyTheContactReplyType() {
        assertThat(provider.supports()).isEqualTo(EmailType.CONTACT_REPLY);
    }
}
