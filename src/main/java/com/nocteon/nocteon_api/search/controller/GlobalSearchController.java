package com.nocteon.nocteon_api.search.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.search.dto.response.GlobalSearchResponse;
import com.nocteon.nocteon_api.search.service.GlobalSearchService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class GlobalSearchController {

    private final GlobalSearchService globalSearchService;

    @GetMapping("/api/dashboard/search")
    @PreAuthorize("hasAuthority('dashboard:read')")
    public ResponseEntity<ApiResponse<GlobalSearchResponse>> search(@RequestParam String query) {
        return ResponseEntity.ok(ApiResponse.success(globalSearchService.search(query), "Search results retrieved"));
    }
}