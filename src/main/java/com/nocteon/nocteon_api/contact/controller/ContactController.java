package com.nocteon.nocteon_api.contact.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.exception.RateLimitExceededException;
import com.nocteon.nocteon_api.common.ratelimit.RateLimiterService;
import com.nocteon.nocteon_api.contact.dto.request.ContactRequest;
import com.nocteon.nocteon_api.contact.dto.response.ContactResponse;
import com.nocteon.nocteon_api.contact.dto.response.ContactStatsResponse;
import com.nocteon.nocteon_api.contact.entity.ContactStatus;
import com.nocteon.nocteon_api.contact.service.ContactService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@Validated
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;
    private final RateLimiterService rateLimiterService;

    @PostMapping("/contact")
    public ResponseEntity<ApiResponse<ContactResponse>> submit(
            @Valid @RequestBody ContactRequest request,
            HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        if (!rateLimiterService.tryConsumeContact(ip)) {
            throw new RateLimitExceededException();
        }
        ContactResponse response = contactService.submit(request, ip);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Message sent successfully"));
    }

    @GetMapping("/dashboard/contact-messages/stats/summary")
    @PreAuthorize("hasAuthority('contact:read')")
    public ResponseEntity<ApiResponse<ContactStatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(contactService.getStats(), "Contact stats retrieved"));
    }

    @GetMapping("/dashboard/contact-messages")
    @PreAuthorize("hasAuthority('contact:read')")
    public ResponseEntity<ApiResponse<PageResponse<ContactResponse>>> getAll(
            @Valid
            @ModelAttribute BaseFilterRequest filter) {
        return ResponseEntity.ok(ApiResponse.success(contactService.getAll(filter), "Contact messages retrieved"));
    }

    @GetMapping("/dashboard/contact-messages/unread")
    @PreAuthorize("hasAuthority('contact:read')")
    public ResponseEntity<ApiResponse<PageResponse<ContactResponse>>> getAllUnread(
            @Valid
            @ModelAttribute BaseFilterRequest filter) {
        return ResponseEntity.ok(ApiResponse.success(contactService.getAllUnread(filter), "Unread messages retrieved"));
    }

    @GetMapping("/dashboard/contact-messages/status/{status}")
    @PreAuthorize("hasAuthority('contact:read')")
    public ResponseEntity<ApiResponse<PageResponse<ContactResponse>>> getByStatus(
            @PathVariable ContactStatus status,
            @Valid
            @ModelAttribute BaseFilterRequest filter) {
        return ResponseEntity.ok(ApiResponse.success(contactService.getAllByStatus(status, filter), "Contact messages retrieved"));
    }

    @GetMapping("/dashboard/contact-messages/{id}")
    @PreAuthorize("hasAuthority('contact:read')")
    public ResponseEntity<ApiResponse<ContactResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(contactService.getById(id), "Contact message retrieved"));
    }

    @PatchMapping("/dashboard/contact-messages/{id}/read")
    @PreAuthorize("hasAuthority('contact:update')")
    public ResponseEntity<ApiResponse<ContactResponse>> markAsRead(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(contactService.markAsRead(id), "Message marked as read"));
    }

    @PostMapping("/dashboard/contact-messages/{id}/reply")
    @PreAuthorize("hasAuthority('contact:update')")
    public ResponseEntity<ApiResponse<ContactResponse>> reply(
            @PathVariable Long id,
            @RequestBody @Valid ReplyRequest request) {
        return ResponseEntity.ok(ApiResponse.success(contactService.reply(id, request.getReplyContent()), "Reply sent"));
    }


    @PostMapping("/dashboard/contact-messages/{id}/resend-email")
    @PreAuthorize("hasAuthority('contact:update')")
    public ResponseEntity<ApiResponse<ContactResponse>> resendEmail(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success(contactService.resendEmail(id), "Reply email re-sent"));
    }

    @DeleteMapping("/dashboard/contact-messages/{id}")
    @PreAuthorize("hasAuthority('contact:delete')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        contactService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/dashboard/contact-messages/{id}/archive")
    @PreAuthorize("hasAuthority('contact:update')")
    public ResponseEntity<ApiResponse<ContactResponse>> archive(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(contactService.archive(id), "Message archived"));
    }

    // ── Inner DTO for reply request ─────────────────────────────────────────────

    @jakarta.validation.constraints.NotBlank
    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    public static class ReplyRequest {
        @jakarta.validation.constraints.NotBlank(message = "Reply content is required")
        @jakarta.validation.constraints.Size(max = 2000, message = "Reply must be at most 2000 characters")
        private String replyContent;
    }
}
