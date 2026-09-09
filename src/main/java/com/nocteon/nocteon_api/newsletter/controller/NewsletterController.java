package com.nocteon.nocteon_api.newsletter.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.newsletter.dto.SubscribeRequest;
import com.nocteon.nocteon_api.newsletter.enums.SubscriptionResult;
import com.nocteon.nocteon_api.newsletter.service.NewsletterService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/newsletter")
@RequiredArgsConstructor
public class NewsletterController {

    private final NewsletterService newsletterService;

    @PostMapping("/subscribe")
    public ResponseEntity<ApiResponse<Void>> subscribe(@Valid @RequestBody SubscribeRequest request) {
        SubscriptionResult result = newsletterService.subscribe(request.getEmail());

        return switch (result) {
            case ALREADY_SUBSCRIBED -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("This email is already subscribed"));
            case RE_SUBSCRIBED -> ResponseEntity.ok(
                    ApiResponse.success(null, "Subscription renewed"));
            case SUBSCRIBED -> ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(null, "Subscription confirmed"));
        };
    }

    @GetMapping("/unsubscribe")
    public ResponseEntity<ApiResponse<Void>> unsubscribe(@RequestParam String token) {
        newsletterService.unsubscribe(token);
        return ResponseEntity.ok(ApiResponse.success(null, "Unsubscribed successfully"));
    }
}