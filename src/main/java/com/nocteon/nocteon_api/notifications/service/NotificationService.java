package com.nocteon.nocteon_api.notifications.service;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.notifications.dto.NotificationCursorResponse;
import com.nocteon.nocteon_api.notifications.dto.NotificationResponse;
import com.nocteon.nocteon_api.notifications.entity.Notification;
import com.nocteon.nocteon_api.notifications.exception.NotificationNotFoundException;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidCursorException;
import com.nocteon.nocteon_api.notifications.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
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
        boolean hasCursor = cursor != null && !cursor.isBlank();
        Instant cursorCreatedAt = null;
        Long cursorId = null;
        if (hasCursor) {
            String[] parts = cursor.split("_", 2);
            if (parts.length != 2) {
                throw new InvalidCursorException();
            }
            if (!parts[1].matches("[0-9]+")) {
                throw new InvalidCursorException();
            }
            try {
                cursorId = Long.parseLong(parts[1]);
            } catch (NumberFormatException ex) {
                throw new InvalidCursorException();
            }
            if (cursorId <= 0) {
                throw new InvalidCursorException();
            }
            cursorCreatedAt = parseCursorTimestamp(parts[0], cursorId, userId);
        }
        int size = Math.min(Math.max(limit, 1), 100);
        Slice<Notification> page;
        if (!hasCursor) {
            page = notificationRepository.findPage(
                    userId, unreadOnly, PageRequest.of(0, size));
        } else {
            page = notificationRepository.findCursorByKeyset(
                    userId, unreadOnly, cursorCreatedAt, cursorId, PageRequest.of(0, size));
        }

        var content = page.getContent().stream().map(this::toResponse).toList();

        String nextCursor = null;
        boolean hasMore = page.hasNext();
        if (hasMore && !page.getContent().isEmpty()) {
            Notification last = page.getContent().get(page.getContent().size() - 1);
            nextCursor = last.getCreatedAt().toString() + "_" + last.getId();
        }

        return NotificationCursorResponse.builder()
                .content(content)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getNotificationsPage(Long userId, int page, int size, boolean unreadOnly) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        Page<Notification> result = notificationRepository.findPage(
                userId, unreadOnly, PageRequest.of(safePage, safeSize));
        return PageResponse.of(result, this::toResponse);
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

    private Instant parseCursorTimestamp(String value, Long cursorId, Long userId) {
        try {
            return Instant.parse(value);
        } catch (java.time.format.DateTimeParseException ignored) {
            // Preserve compatibility with cursors issued before the lossless
            // ISO-8601 format was introduced. Resolve the stored notification so
            // its full createdAt precision is used instead of truncating the
            // timestamp to milliseconds.
            if (value.matches("[0-9]+")) {
                long legacyMillis;
                try {
                    legacyMillis = Long.parseLong(value);
                } catch (NumberFormatException ex) {
                    throw new InvalidCursorException();
                }
                Instant stored = notificationRepository.findByIdAndRecipientUserId(cursorId, userId)
                        .map(com.nocteon.nocteon_api.notifications.entity.Notification::getCreatedAt)
                        .orElseThrow(InvalidCursorException::new);
                // The legacy cursor must describe the row it references; reject a
                // mismatched prefix instead of silently trusting the id.
                if (stored.toEpochMilli() != legacyMillis) {
                    throw new InvalidCursorException();
                }
                return stored;
            }
            throw new InvalidCursorException();
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
