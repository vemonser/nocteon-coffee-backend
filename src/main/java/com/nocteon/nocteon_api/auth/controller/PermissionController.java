package com.nocteon.nocteon_api.auth.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.enums.Permission;

@RestController
@RequestMapping("/api/dashboard/permissions")
public class PermissionController {

    @GetMapping
    @PreAuthorize("hasAuthority('user:read') or hasAuthority('role:read')")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getAll() {
        List<Map<String, String>> list = Arrays.stream(Permission.values())
                .map(p -> {
                    String slug = p.getPermission();
                    String[] parts = slug.split(":", 2);
                    return Map.of("slug", slug, "resource", parts[0], "action", parts.length > 1 ? parts[1] : "");
                })
                .toList();
        return ResponseEntity.ok(ApiResponse.success(list, "Permissions retrieved successfully"));
    }
}
