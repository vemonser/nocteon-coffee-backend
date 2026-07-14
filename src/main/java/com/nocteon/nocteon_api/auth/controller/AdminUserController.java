package com.nocteon.nocteon_api.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nocteon.nocteon_api.auth.dto.request.AdminRegisterRequest;
import com.nocteon.nocteon_api.auth.dto.request.AdminUpdateUserRequest;
import com.nocteon.nocteon_api.auth.dto.request.UserFilterRequest;
import com.nocteon.nocteon_api.auth.dto.response.UserResponse;
import com.nocteon.nocteon_api.auth.service.UserService;
import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard/users")
@RequiredArgsConstructor
public class AdminUserController {

        private final UserService userService;

        @GetMapping
        @PreAuthorize("hasAuthority('user:read')")
        public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getAll(
                        @ModelAttribute UserFilterRequest filter) {

                return ResponseEntity.ok(ApiResponse.success(
                                userService.getAllForAdmin(filter),
                                "Users retrieved successfully"));
        }

        @PostMapping
        @PreAuthorize("hasAuthority('user:create')")
        public ResponseEntity<ApiResponse<UserResponse>> create(
                        @RequestBody @Valid AdminRegisterRequest request) {
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success(
                                                userService.adminCreate(request),
                                                "User created successfully"));
        }

        @PutMapping("/{id}")
        @PreAuthorize("hasAuthority('user:update')")
        public ResponseEntity<ApiResponse<UserResponse>> update(
                        @PathVariable Long id,
                        @RequestBody @Valid AdminUpdateUserRequest request) {
                return ResponseEntity.ok(ApiResponse.success(
                                userService.adminUpdate(id, request),
                                "User updated successfully"));
        }

        @PatchMapping("/{id}/toggle-active")
        @PreAuthorize("hasAuthority('user:update')")
        public ResponseEntity<ApiResponse<UserResponse>> toggleActive(@PathVariable Long id) {
                UserResponse user = userService.toggleActive(id);
                String message = user.isActive() ? "User unblocked successfully" : "User blocked successfully";
                return ResponseEntity.ok(ApiResponse.success(user, message));
        }
}
