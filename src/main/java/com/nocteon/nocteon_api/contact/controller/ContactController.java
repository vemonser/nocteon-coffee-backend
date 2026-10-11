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

    /**
     * Inbox for staff.
     *
     * <p>The class mapping is {@code /api}, not {@code /api/contact}, so this composes to
     * {@code /api/dashboard/contact-messages}: both the path the dashboard calls and the prefix
     * the dashboard security rule matches.
     *
     * <p>It was originally {@code /api/contact} plus a method mapping of
     * {@code /api/dashboard/contact-messages}, which Spring concatenates into
     * {@code /api/contact/api/dashboard/contact-messages}. Fixing only the method mapping repaired
     * the 404 but not the other half: the result still did not begin with {@code /api/dashboard/},
     * so it fell through to any-request-authenticated rather than the dashboard rule. It was not
     * exploitable, since {@code contact:read} is the real control and only ADMIN holds it, but a
     * staff inbox ended up outside the rule meant to cover staff endpoints.
     */
    @GetMapping("/dashboard/contact-messages")
    @PreAuthorize("hasAuthority('contact:read')")
    public ResponseEntity<ApiResponse<PageResponse<ContactResponse>>> getAll(
            @ModelAttribute BaseFilterRequest filter) {
        return ResponseEntity.ok(ApiResponse.success(contactService.getAll(filter), "Contact messages retrieved"));
    }
}