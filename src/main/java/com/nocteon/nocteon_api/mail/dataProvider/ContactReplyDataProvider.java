package com.nocteon.nocteon_api.mail.dataProvider;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.nocteon.nocteon_api.contact.entity.ContactMessage;
import com.nocteon.nocteon_api.contact.repository.ContactMessageRepository;
import com.nocteon.nocteon_api.mail.enums.EmailType;

import lombok.RequiredArgsConstructor;

/**
 * The params of a contact reply email.
 *
 * <p>The map is a {@link HashMap}, not {@code Map.of()}: a message that has
 * not been answered yet has a null reply, and {@code Map.of()} rejects a null
 * value — the failure would surface as a NullPointerException in the middle
 * of a send.
 */
@Component
@RequiredArgsConstructor
public class ContactReplyDataProvider implements EmailTemplateDataProvider {

    private final ContactMessageRepository contactMessageRepository;

    @Override
    public EmailType supports() {
        return EmailType.CONTACT_REPLY;
    }

    @Override
    public Map<String, Object> buildParams(Long contactMessageId) {
        ContactMessage message = contactMessageRepository.findById(contactMessageId)
                .orElseThrow(() -> new IllegalStateException(
                        "Contact message not found: " + contactMessageId));

        Map<String, Object> params = new HashMap<>();
        params.put("recipientName", message.getName());
        params.put("originalSubject", message.getSubject());
        params.put("originalMessage", message.getMessage());
        params.put("replyContent", message.getReplyContent());
        return params;
    }
}
