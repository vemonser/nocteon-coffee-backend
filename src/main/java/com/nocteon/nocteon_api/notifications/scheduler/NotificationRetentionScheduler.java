package com.nocteon.nocteon_api.notifications.scheduler;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.nocteon.nocteon_api.notifications.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationRetentionScheduler {

    private final NotificationRepository notificationRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanup() {
        Instant now = Instant.now();
        int readDeleted = notificationRepository.deleteReadOlderThan(now.minus(30, ChronoUnit.DAYS));
        int allDeleted = notificationRepository.deleteAllOlderThan(now.minus(90, ChronoUnit.DAYS));
        log.info("Notification retention cleanup: read>30d={}, all>90d={}", readDeleted, allDeleted);
    }
}
