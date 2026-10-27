package com.nocteon.nocteon_api.notifications.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;

import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.notifications.dto.NotificationIdsRequest;
import com.nocteon.nocteon_api.notifications.service.NotificationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard/notifications")
@Validated
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("hasAuthority('notification:read')")
    public ApiResponse<?> getNotifications(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (page != null) {
            int s = (size != null && size > 0) ? size : 20;
            return ApiResponse.success(
                    notificationService.getNotificationsPage(principal.getUserId(), page, s, unreadOnly),
                    "Notifications retrieved");
        }
        // Keep `limit` as the historical cursor-page parameter while also
        // honoring `size` when a caller uses the page-mode name.
        int cursorLimit = (size != null && size > 0) ? size : limit;
        return ApiResponse.success(
                notificationService.getNotifications(principal.getUserId(), cursor, cursorLimit, unreadOnly),
                "Notifications retrieved");
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAuthority('notification:read')")
    public ApiResponse<Long> getUnreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(notificationService.getUnreadCount(principal.getUserId()), "Unread count retrieved");
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("hasAuthority('notification:update')")
    public ApiResponse<Void> markRead(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markRead(principal.getUserId(), id);
        return ApiResponse.success(null, "Marked as read");
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("hasAuthority('notification:update')")
    public ApiResponse<Void> markReadPatch(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markRead(principal.getUserId(), id);
        return ApiResponse.success(null, "Marked as read");
    }

    @PostMapping("/{id}/unread")
    @PreAuthorize("hasAuthority('notification:update')")
    public ApiResponse<Void> markUnread(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markUnread(principal.getUserId(), id);
        return ApiResponse.success(null, "Marked as unread");
    }

    @PatchMapping("/{id}/unread")
    @PreAuthorize("hasAuthority('notification:update')")
    public ApiResponse<Void> markUnreadPatch(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markUnread(principal.getUserId(), id);
        return ApiResponse.success(null, "Marked as unread");
    }

    @PatchMapping("/bulk-read")
    @PreAuthorize("hasAuthority('notification:update')")
    public ApiResponse<Void> markReadBulk(
            @Valid @RequestBody NotificationIdsRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markReadBulk(principal.getUserId(), request.getIds());
        return ApiResponse.success(null, "Marked as read");
    }

    @PostMapping("/bulk-delete")
    @PreAuthorize("hasAuthority('notification:delete')")
    public ApiResponse<Void> deleteBulk(
            @Valid @RequestBody NotificationIdsRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        notificationService.deleteBulk(principal.getUserId(), request.getIds());
        return ApiResponse.success(null, "Notifications deleted");
    }

    @PostMapping("/read-all")
    @PreAuthorize("hasAuthority('notification:update')")
    public ApiResponse<Void> markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markAllRead(principal.getUserId());
        return ApiResponse.success(null, "All marked as read");
    }

    @PatchMapping("/read-all")
    @PreAuthorize("hasAuthority('notification:update')")
    public ApiResponse<Void> markAllReadPatch(@AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markAllRead(principal.getUserId());
        return ApiResponse.success(null, "All marked as read");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('notification:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        notificationService.delete(principal.getUserId(), id);
        return ApiResponse.success(null, "Notification deleted");
    }
}
