package com.nocteon.nocteon_api.broadcast.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import com.nocteon.nocteon_api.broadcast.dto.request.CreateBroadcastRequest;
import com.nocteon.nocteon_api.broadcast.dto.response.BroadcastResponse;
import com.nocteon.nocteon_api.broadcast.service.BroadcastCreationService;
import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@Validated
@RequiredArgsConstructor
public class BroadcastController {

    private final BroadcastCreationService broadcastCreationService;

    @PostMapping("/api/dashboard/broadcasts")
    @PreAuthorize("hasAuthority('broadcast:create')")
    public ResponseEntity<ApiResponse<BroadcastResponse>> create(@RequestBody @Valid CreateBroadcastRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(broadcastCreationService.createBroadcast(request), "Broadcast created"));
    }

    @GetMapping("/api/dashboard/broadcasts")
    @PreAuthorize("hasAuthority('broadcast:read')")
    public ResponseEntity<ApiResponse<PageResponse<BroadcastResponse>>> getAll(
            @ModelAttribute BaseFilterRequest filter) {
        return ResponseEntity.ok(ApiResponse.success(broadcastCreationService.getAll(filter), "Broadcasts retrieved"));
    }

    @GetMapping("/api/dashboard/broadcasts/{id}")
    @PreAuthorize("hasAuthority('broadcast:read')")
    public ResponseEntity<ApiResponse<BroadcastResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(broadcastCreationService.getById(id), "Broadcast retrieved"));
    }
}