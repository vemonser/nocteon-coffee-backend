package com.nocteon.nocteon_api.notifications.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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

    /** One statement for a whole selection; ids that are not the caller's are untouched. */
    @Modifying
    @Query("""
            UPDATE Notification n SET n.readAt = :now
            WHERE n.id IN :ids AND n.recipientUserId = :recipientId AND n.readAt IS NULL
            """)
    int markReadBulk(@Param("ids") Collection<Long> ids,
                     @Param("recipientId") Long recipientId,
                     @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.id IN :ids AND n.recipientUserId = :recipientId")
    int deleteBulk(@Param("ids") Collection<Long> ids, @Param("recipientId") Long recipientId);

    @Modifying
    @Query(value = """
            INSERT INTO notifications
                (recipient_user_id, type, params, entity_type, entity_id, created_at, updated_at)
            VALUES (:recipientId, :type, :params, :entityType, :entityId, now(), now())
            ON CONFLICT (recipient_user_id, type, entity_type, entity_id) DO NOTHING
            """, nativeQuery = true)
    int insertIgnore(@Param("recipientId") Long recipientId,
                     @Param("type") String type,
                     @Param("params") String params,
                     @Param("entityType") String entityType,
                     @Param("entityId") Long entityId);

    /**
     * Cursor-keyset query. Both cursor values are required: first-page requests
     * must use {@link #findPage(Long, boolean, Pageable)} instead. There is no
     * nullable convenience wrapper, so a missing cursor cannot be mistaken for a
     * valid keyset and no nullable SQL parameter is bound.
     */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.recipientUserId = :recipientId
              AND (:unreadOnly = false OR n.readAt IS NULL)
              AND (n.createdAt < :cursorCreatedAt
                    OR (n.createdAt = :cursorCreatedAt AND n.id < :cursorId))
            ORDER BY n.createdAt DESC, n.id DESC
            """)
    Slice<Notification> findCursorByKeyset(@Param("recipientId") Long recipientId,
                                          @Param("unreadOnly") boolean unreadOnly,
                                          @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                          @Param("cursorId") Long cursorId,
                                          Pageable pageable);

    @Query("""
            SELECT n FROM Notification n
            WHERE n.recipientUserId = :recipientId
              AND (:unreadOnly = false OR n.readAt IS NULL)
            ORDER BY n.createdAt DESC, n.id DESC
            """)
    Page<Notification> findPage(@Param("recipientId") Long recipientId,
                               @Param("unreadOnly") boolean unreadOnly,
                               Pageable pageable);

    List<Notification> findByRecipientUserIdAndReadAtIsNull(Long recipientUserId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.readAt IS NOT NULL AND n.readAt < :cutoff")
    int deleteReadOlderThan(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoff")
    int deleteAllOlderThan(@Param("cutoff") Instant cutoff);
}
