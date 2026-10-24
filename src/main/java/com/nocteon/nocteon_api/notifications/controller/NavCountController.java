package com.nocteon.nocteon_api.notifications.controller;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.notifications.service.NavCountService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class NavCountController {

    private final NavCountService navCountService;

    @GetMapping("/nav-counts")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Long>> navCounts(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(navCountService.countsFor(principal), "Nav counts retrieved");
    }
}
