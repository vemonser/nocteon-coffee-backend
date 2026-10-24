package com.nocteon.nocteon_api.notifications.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.notifications.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByIdAndRecipientUserId(Long id, Long recipientUserId);

    long countByRecipientUserIdAndReadAtIsNull(Long recipientUserId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.id = :id AND n.recipientUserId = :recipientId")
    int markRead(@Param("id") Long id, @Param("recipientId") Long recipientId, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = NULL WHERE n.id = :id AND n.recipientUserId = :recipientId")
    int markUnread(@Param("id") Long id, @Param("recipientId") Long recipientId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.recipientUserId = :recipientId AND n.readAt IS NULL")
    int markAllRead(@Param("recipientId") Long recipientId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.id = :id AND n.recipientUserId = :recipientId")
    int deleteByIdAndRecipient(@Param("id") Long id, @Param("recipientId") Long recipientId);

    @Query("""
            SELECT n FROM Notification n
            WHERE n.recipientUserId = :recipientId
              AND (:unreadOnly = false OR n.readAt IS NULL)
              AND (:cursorCreatedAt IS NULL OR n.createdAt < :cursorCreatedAt
                    OR (n.createdAt = :cursorCreatedAt AND n.id < :cursorId))
            ORDER BY n.createdAt DESC, n.id DESC
            """)
    Page<Notification> findCursor(@Param("recipientId") Long recipientId,
                                 @Param("unreadOnly") boolean unreadOnly,
                                 @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                 @Param("cursorId") Long cursorId,
                                 Pageable pageable);

    List<Notification> findByRecipientUserIdAndReadAtIsNull(Long recipientUserId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.readAt IS NOT NULL AND n.readAt < :cutoff")
    int deleteReadOlderThan(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoff")
    int deleteAllOlderThan(@Param("cutoff") Instant cutoff);
}
