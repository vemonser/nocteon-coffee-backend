package com.nocteon.nocteon_api.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.notifications.dto.NotificationCursorResponse;
import com.nocteon.nocteon_api.notifications.dto.NotificationResponse;
import com.nocteon.nocteon_api.notifications.entity.Notification;
import com.nocteon.nocteon_api.notifications.exception.NotificationNotFoundException;
import com.nocteon.nocteon_api.notifications.repository.NotificationRepository;
import com.nocteon.nocteon_api.notifications.service.NotificationService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * Covers the notification operations that the pagination-focused suite does not touch:
 * ownership scoping, unread filtering/counting, mark read/unread/all, and delete.
 * Recipient ids are synthetic because notifications have no FK to users.
 */
@SpringBootTest(classes = NocteonApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NotificationOperationsIntegrationTest {

    private static final long USER_A = 900_001L;
    private static final long USER_B = 900_002L;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from notifications where recipient_user_id in (?, ?)", USER_A, USER_B);
    }

    private Notification create(long recipient, String type, long entityId, Instant readAt) {
        return notificationRepository.save(Notification.builder()
                .recipientUserId(recipient)
                .type(type)
                .params("{}")
                .entityType("test")
                .entityId(entityId)
                .readAt(readAt)
                .build());
    }

    @Test
    void listsOnlyOwnNotificationsAndFiltersUnread() {
        Notification aUnread = create(USER_A, "test.a.unread", 1L, null);
        Notification aRead = create(USER_A, "test.a.read", 2L, Instant.now());
        create(USER_B, "test.b.unread", 3L, null);

        NotificationCursorResponse all = notificationService.getNotifications(USER_A, null, 10, false);
        assertThat(all.getContent()).extracting(NotificationResponse::getId)
                .containsExactlyInAnyOrder(aUnread.getId(), aRead.getId());

        NotificationCursorResponse unread = notificationService.getNotifications(USER_A, null, 10, true);
        assertThat(unread.getContent()).extracting(NotificationResponse::getId)
                .containsExactly(aUnread.getId());

        assertThat(notificationService.getUnreadCount(USER_A)).isEqualTo(1);
        assertThat(notificationService.getUnreadCount(USER_B)).isEqualTo(1);
    }

    @Test
    void markReadUnreadAndAllRead() {
        Notification first = create(USER_A, "test.mr.one", 11L, null);
        Notification second = create(USER_A, "test.mr.two", 12L, null);

        assertThat(notificationService.getUnreadCount(USER_A)).isEqualTo(2);

        notificationService.markRead(USER_A, first.getId());
        assertThat(notificationService.getUnreadCount(USER_A)).isEqualTo(1);
        assertThat(notificationRepository.findById(first.getId()).orElseThrow().getReadAt()).isNotNull();

        notificationService.markUnread(USER_A, first.getId());
        assertThat(notificationService.getUnreadCount(USER_A)).isEqualTo(2);
        assertThat(notificationRepository.findById(first.getId()).orElseThrow().getReadAt()).isNull();

        notificationService.markAllRead(USER_A);
        assertThat(notificationService.getUnreadCount(USER_A)).isZero();
        assertThat(notificationRepository.findById(second.getId()).orElseThrow().getReadAt()).isNotNull();
    }

    @Test
    void operationsAreScopedToOwner() {
        Notification ownedByB = create(USER_B, "test.owner.b", 21L, null);

        assertThatThrownBy(() -> notificationService.markRead(USER_A, ownedByB.getId()))
                .isInstanceOf(NotificationNotFoundException.class);
        assertThatThrownBy(() -> notificationService.markUnread(USER_A, ownedByB.getId()))
                .isInstanceOf(NotificationNotFoundException.class);
        assertThatThrownBy(() -> notificationService.delete(USER_A, ownedByB.getId()))
                .isInstanceOf(NotificationNotFoundException.class);

        Notification stillThere = notificationRepository.findById(ownedByB.getId()).orElseThrow();
        assertThat(stillThere.getRecipientUserId()).isEqualTo(USER_B);
        assertThat(stillThere.getReadAt()).isNull();
    }

    @Test
    void deletesOwnNotificationOnlyOnce() {
        Notification own = create(USER_A, "test.delete", 31L, null);

        notificationService.delete(USER_A, own.getId());

        assertThat(notificationRepository.findById(own.getId())).isEmpty();
        assertThatThrownBy(() -> notificationService.delete(USER_A, own.getId()))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    void pageModeRespectsUnreadFilterAndPagination() {
        create(USER_A, "test.page.1", 41L, null);
        create(USER_A, "test.page.2", 42L, null);
        create(USER_A, "test.page.3", 43L, null);
        create(USER_A, "test.page.4", 44L, Instant.now());
        create(USER_A, "test.page.5", 45L, Instant.now());

        PageResponse<NotificationResponse> firstPage =
                notificationService.getNotificationsPage(USER_A, 0, 2, true);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.isLast()).isFalse();

        PageResponse<NotificationResponse> secondPage =
                notificationService.getNotificationsPage(USER_A, 1, 2, true);
        assertThat(secondPage.getContent()).hasSize(1);
        assertThat(secondPage.isLast()).isTrue();
    }

    @Test
    @Transactional
    void idempotentInsertDoesNotDuplicateOrPoisonTheBatch() {
        int first = notificationRepository.insertIgnore(USER_A, "test.idem", "{}", "test", 61L);
        int duplicate = notificationRepository.insertIgnore(USER_A, "test.idem", "{}", "test", 61L);
        int differentEntity = notificationRepository.insertIgnore(USER_A, "test.idem", "{}", "test", 62L);

        assertThat(first).isEqualTo(1);
        assertThat(duplicate).isZero();
        assertThat(differentEntity).isEqualTo(1);
        assertThat(notificationRepository.countByRecipientUserIdAndReadAtIsNull(USER_A)).isEqualTo(2);
    }

    @Test
    void cursorModeNeverReturnsAnotherUsersRows() {
        Notification a1 = create(USER_A, "test.cursor.a1", 51L, null);
        Notification a2 = create(USER_A, "test.cursor.a2", 52L, null);
        create(USER_B, "test.cursor.b1", 53L, null);

        NotificationCursorResponse first = notificationService.getNotifications(USER_A, null, 1, false);
        assertThat(first.getContent()).extracting(NotificationResponse::getId)
                .containsExactly(a2.getId());
        assertThat(first.isHasMore()).isTrue();

        NotificationCursorResponse second =
                notificationService.getNotifications(USER_A, first.getNextCursor(), 1, false);
        assertThat(second.getContent()).extracting(NotificationResponse::getId)
                .containsExactly(a1.getId());
        assertThat(second.isHasMore()).isFalse();

        List<Long> seen = List.of(
                first.getContent().get(0).getId(),
                second.getContent().get(0).getId());
        assertThat(seen).doesNotHaveDuplicates();
    }
}
