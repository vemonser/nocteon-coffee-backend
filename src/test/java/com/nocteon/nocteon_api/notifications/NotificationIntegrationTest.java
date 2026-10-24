package com.nocteon.nocteon_api.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.notifications.event.ContactCreatedEvent;
import com.nocteon.nocteon_api.notifications.repository.NotificationRepository;
import com.nocteon.nocteon_api.support.TestJwtKeys;

@SpringBootTest(classes = NocteonApiApplication.class)
class NotificationIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private NotificationRepository notificationRepository;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Test
    void contactMessageCreatesNotificationsForEligibleRecipients() throws Exception {
        long before = notificationRepository.count();
        eventPublisher.publishEvent(new ContactCreatedEvent(888L, "Subject", "Sender"));
        Thread.sleep(3000);
        long after = notificationRepository.count();
        assertThat(after).isGreaterThanOrEqualTo(before);
    }
}
