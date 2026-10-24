package com.nocteon.nocteon_api.notifications.service;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.notifications.dto.NotificationCursorResponse;
import com.nocteon.nocteon_api.notifications.dto.NotificationResponse;
import com.nocteon.nocteon_api.notifications.entity.Notification;
import com.nocteon.nocteon_api.notifications.exception.NotificationNotFoundException;
import com.nocteon.nocteon_api.notifications.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public NotificationCursorResponse getNotifications(Long userId, String cursor, int limit, boolean unreadOnly) {
        Instant cursorCreatedAt = null;
        Long cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            String[] parts = cursor.split("_", 2);
            if (parts.length == 2) {
                cursorCreatedAt = Instant.ofEpochMilli(Long.parseLong(parts[0]));
                cursorId = Long.parseLong(parts[1]);
            }
        }
        int size = Math.min(Math.max(limit, 1), 100);
        Page<Notification> page = notificationRepository.findCursor(
                userId, unreadOnly, cursorCreatedAt, cursorId, PageRequest.of(0, size));

        var content = page.getContent().stream().map(this::toResponse).toList();

        String nextCursor = null;
        boolean hasMore = page.hasNext();
        if (hasMore && !page.getContent().isEmpty()) {
            Notification last = page.getContent().get(page.getContent().size() - 1);
            nextCursor = last.getCreatedAt().toEpochMilli() + "_" + last.getId();
        }

        return NotificationCursorResponse.builder()
                .content(content)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByRecipientUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        int updated = notificationRepository.markRead(id, userId, Instant.now());
        if (updated == 0) {
            throw new NotificationNotFoundException();
        }
    }

    @Transactional
    public void markUnread(Long userId, Long id) {
        int updated = notificationRepository.markUnread(id, userId);
        if (updated == 0) {
            throw new NotificationNotFoundException();
        }
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId, Instant.now());
    }

    @Transactional
    public void delete(Long userId, Long id) {
        int deleted = notificationRepository.deleteByIdAndRecipient(id, userId);
        if (deleted == 0) {
            throw new NotificationNotFoundException();
        }
    }

    private NotificationResponse toResponse(Notification n) {
        Object params = null;
        if (n.getParams() != null && !n.getParams().isBlank()) {
            try {
                params = objectMapper.readValue(n.getParams(), Object.class);
            } catch (Exception e) {
                params = n.getParams();
            }
        }
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .params(params)
                .entityType(n.getEntityType())
                .entityId(n.getEntityId())
                .createdAt(n.getCreatedAt())
                .readAt(n.getReadAt())
                .build();
    }
}
