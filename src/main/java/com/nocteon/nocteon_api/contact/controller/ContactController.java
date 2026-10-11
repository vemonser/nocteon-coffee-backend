package com.nocteon.nocteon_api.contact.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.exception.RateLimitExceededException;
import com.nocteon.nocteon_api.common.ratelimit.RateLimiterService;
import com.nocteon.nocteon_api.contact.dto.request.ContactRequest;
import com.nocteon.nocteon_api.contact.dto.response.ContactResponse;
import com.nocteon.nocteon_api.contact.service.ContactService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/contact")
@Validated
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;
    private final RateLimiterService rateLimiterService;

    @PostMapping
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

    /**
     * Inbox for staff.
     *
     * <p>The mapping is relative to the class-level {@code /api/contact}. It previously read
     * {@code /api/dashboard/contact-messages}, which Spring composes to
     * {@code /api/contact/api/dashboard/contact-messages} — a path no client would guess, so the
     * dashboard's contact inbox 404'd. Worse, the composed path no longer starts with
     * {@code /api/dashboard/}, so it also escaped the dashboard security rule and fell through to
     * any-request-authenticated.
     */
    @GetMapping("/dashboard/contact-messages")
    @PreAuthorize("hasAuthority('contact:read')")
    public ResponseEntity<ApiResponse<PageResponse<ContactResponse>>> getAll(
            @ModelAttribute BaseFilterRequest filter) {
        return ResponseEntity.ok(ApiResponse.success(contactService.getAll(filter), "Contact messages retrieved"));
    }
}