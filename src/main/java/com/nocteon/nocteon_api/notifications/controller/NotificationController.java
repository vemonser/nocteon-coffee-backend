package com.nocteon.nocteon_api.notifications.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.notifications.dto.NotificationResponse;
import com.nocteon.nocteon_api.notifications.service.NotificationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("hasAuthority('notification:read')")
    public ApiResponse<PageResponse<NotificationResponse>> getNotifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                notificationService.getNotifications(unreadOnly, PageRequest.of(page, size)),
                "Notifications retrieved");
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAuthority('notification:read')")
    public ApiResponse<Long> getUnreadCount() {
        return ApiResponse.success(notificationService.getUnreadCount(), "Unread count retrieved");
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("hasAuthority('notification:read')")
    public ApiResponse<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ApiResponse.success(null, "Marked as read");
    }

    @PatchMapping("/read-all")
    @PreAuthorize("hasAuthority('notification:read')")
    public ApiResponse<Void> markAllAsRead() {
        notificationService.markAllAsRead();
        return ApiResponse.success(null, "All marked as read");
    }
}
