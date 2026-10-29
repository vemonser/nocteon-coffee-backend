package com.nocteon.nocteon_api.notifications.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final com.nocteon.nocteon_api.notifications.stream.NotificationStreamRegistry streamRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * The one place a notification row is created.
     *
     * <p>The insert is idempotent (the fan-out may run twice for the same entity) and
     * the realtime push happens only when the row was actually inserted, so a
     * repeated event never produces a second bell entry or a second stream message.
     * Publishing is deferred to after-commit by the registry.
     *
     * @return the created row, or empty when the notification already existed
     */
    @Transactional
    public java.util.Optional<NotificationResponse> createAndPublish(
            Long recipientId, String type, String paramsJson, String entityType, Long entityId) {
        int inserted = notificationRepository.insertIgnore(recipientId, type, paramsJson, entityType, entityId);
        if (inserted == 0) {
            // Already delivered for this entity: pushing again would double-count.
            return java.util.Optional.empty();
        }
        Notification saved = findCreated(recipientId, type, entityType, entityId);
        if (saved == null) {
            // The row exists (the insert succeeded) but cannot be re-read in this
            // transaction. The stream is a latency optimisation, so skipping the push
            // is safe: the client's poll still finds the row.
            log.warn("Notification inserted but not re-read (recipient={}, type={}, entity={}/{}); stream push skipped",
                    recipientId, type, entityType, entityId);
            return java.util.Optional.empty();
        }
        NotificationResponse response = toResponse(saved);
        streamRegistry.publishNotification(
                recipientId, response, notificationRepository.countByRecipientUserIdAndReadAtIsNull(recipientId));
        return java.util.Optional.of(response);
    }

    private Notification findCreated(Long recipientId, String type, String entityType, Long entityId) {
        if (entityId == null) {
            // The unique constraint does not cover a null entity id, so there is
            // nothing to look up by; the newest row of this type is the one just
            // inserted by this thread.
            return notificationRepository
                    .findFirstByRecipientUserIdAndTypeOrderByIdDesc(recipientId, type)
                    .orElse(null);
        }
        return notificationRepository
                .findByRecipientUserIdAndTypeAndEntityTypeAndEntityId(recipientId, type, entityType, entityId)
                .orElse(null);
    }

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
        // Fetch one extra row so a terminal page that is exactly full can be
        // distinguished from a page followed by more data (Slice.hasNext() alone
        // cannot tell them apart when the fetch size equals the page size).
        Slice<Notification> page;
        if (!hasCursor) {
            page = notificationRepository.findPage(
                    userId, unreadOnly, PageRequest.of(0, size + 1));
        } else {
            page = notificationRepository.findCursorByKeyset(
                    userId, unreadOnly, cursorCreatedAt, cursorId, PageRequest.of(0, size + 1));
        }

        List<Notification> pageRows = page.getContent().stream().limit(size).toList();
        var content = pageRows.stream().map(this::toResponse).toList();

        boolean hasMore = page.getContent().size() > size;
        String nextCursor = null;
        if (hasMore && !pageRows.isEmpty()) {
            Notification last = pageRows.get(pageRows.size() - 1);
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
        publishUnreadCount(userId);
    }

    @Transactional
    public void markUnread(Long userId, Long id) {
        int updated = notificationRepository.markUnread(id, userId);
        if (updated == 0) {
            throw new NotificationNotFoundException();
        }
        publishUnreadCount(userId);
    }

    /** One query for a selection; missing or foreign ids are simply skipped. */
    @Transactional
    public void markReadBulk(Long userId, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        notificationRepository.markReadBulk(ids, userId, Instant.now());
        publishUnreadCount(userId);
    }

    @Transactional
    public void deleteBulk(Long userId, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        notificationRepository.deleteBulk(ids, userId);
        publishUnreadCount(userId);
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId, Instant.now());
        publishUnreadCount(userId);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        int deleted = notificationRepository.deleteByIdAndRecipient(id, userId);
        if (deleted == 0) {
            throw new NotificationNotFoundException();
        }
        publishUnreadCount(userId);
    }

    /**
     * Pushes the new unread count to this user's live streams, so a second tab's
     * badge does not wait for its next poll. Skipped entirely when nobody is
     * listening, so the count query is not paid for by REST-only clients.
     */
    private void publishUnreadCount(Long userId) {
        if (!streamRegistry.hasSubscribers(userId)) return;
        streamRegistry.publishUnreadCount(
                userId, notificationRepository.countByRecipientUserIdAndReadAtIsNull(userId));
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
