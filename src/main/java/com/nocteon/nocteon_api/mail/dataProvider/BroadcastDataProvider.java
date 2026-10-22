package com.nocteon.nocteon_api.mail.dataProvider;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.nocteon.nocteon_api.broadcast.entity.Broadcast;
import com.nocteon.nocteon_api.broadcast.repository.BroadcastRepository;
import com.nocteon.nocteon_api.mail.enums.EmailType;

import lombok.RequiredArgsConstructor;

/**
 * The data provider for the admin broadcast template.
 *
 * <p>The template lives in Brevo and renders whatever the admin typed as
 * the subject and the body. The store sends one message per recipient, so
 * every recipient gets the same content.
 */
@Component
@RequiredArgsConstructor
public class BroadcastDataProvider implements EmailTemplateDataProvider {

    private final BroadcastRepository broadcastRepository;

    @Override
    public EmailType supports() {
        return EmailType.ADMIN_BROADCAST;
    }

    @Override
    public Map<String, Object> buildParams(Long broadcastId) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new IllegalStateException("Broadcast not found: " + broadcastId));

        return Map.of(
                "subject", broadcast.getSubject(),
                "content", broadcast.getContent());
    }
}
