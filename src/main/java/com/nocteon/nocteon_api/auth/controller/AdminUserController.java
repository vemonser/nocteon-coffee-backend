package com.nocteon.nocteon_api.auth.controller;

import org.springframework.beans.factory.annotation.Autowired;
import java.time.Instant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import org.springframework.validation.annotation.Validated;

import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.auth.enums.Role;
import com.nocteon.nocteon_api.auth.dto.request.AdminRegisterRequest;
import com.nocteon.nocteon_api.auth.dto.request.AdminUpdateUserRequest;
import com.nocteon.nocteon_api.auth.dto.request.UserFilterRequest;
import com.nocteon.nocteon_api.auth.dto.response.UserResponse;
import com.nocteon.nocteon_api.auth.dto.response.RoleResponse;
import com.nocteon.nocteon_api.auth.service.UserService;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.PageResponse;

import java.util.List;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard/users")
@Validated
@RequiredArgsConstructor
public class AdminUserController {

        private final UserService userService;
    @Autowired
    private UserRepository userRepository;

        @GetMapping
        @PreAuthorize("hasAuthority('user:read')")
        public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getAll(
                        @Valid
                        @ModelAttribute UserFilterRequest filter) {

                return ResponseEntity.ok(ApiResponse.success(
                                userService.getAllForAdmin(filter),
                                "Users retrieved successfully"));
        }

        @GetMapping("/{id}")
        @PreAuthorize("hasAuthority('user:read')")
        public ResponseEntity<ApiResponse<UserResponse>> getById(@PathVariable Long id) {
                return ResponseEntity.ok(ApiResponse.success(
                                userService.getById(id),
                                "User retrieved successfully"));
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
                        @AuthenticationPrincipal UserPrincipal principal,
                        @RequestBody @Valid AdminUpdateUserRequest request) {
                // The principal is passed through because the route's own permission is not enough
                // to cover every field in the body: UserService.adminUpdate re-checks a requested
                // role change against user:change_role, which MODERATOR does not hold.
                return ResponseEntity.ok(ApiResponse.success(
                                userService.adminUpdate(id, request, principal),
                                "User updated successfully"));
        }

        @PatchMapping("/{id}/toggle-active")
        @PreAuthorize("hasAuthority('user:update')")
        public ResponseEntity<ApiResponse<UserResponse>> toggleActive(@PathVariable Long id) {
                UserResponse user = userService.toggleActive(id);
                String message = user.isActive() ? "User unblocked successfully" : "User blocked successfully";
                return ResponseEntity.ok(ApiResponse.success(user, message));
        }

        @GetMapping("/roles")
        @PreAuthorize("hasAuthority('user:read')")
        public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
            List<Role> allRoles = List.of(Role.values());
            long totalUsers = userRepository.countByRoleAndCreatedAtBetween(Role.ADMIN, Instant.EPOCH, Instant.now())
                    + userRepository.countByRoleAndCreatedAtBetween(Role.MODERATOR, Instant.EPOCH, Instant.now())
                    + userRepository.countByRoleAndCreatedAtBetween(Role.CUSTOMER, Instant.EPOCH, Instant.now());

            List<RoleResponse> responses = allRoles.stream()
                    .map(role -> RoleResponse.fromRole(
                            role,
                            userRepository.countByRole(role),
                            role.getPermissions().size()))
                    .toList();

            return ResponseEntity.ok(ApiResponse.success(responses, "Roles retrieved successfully"));
        }

        @GetMapping("/roles/{name}")
        @PreAuthorize("hasAuthority('user:read')")
        public ResponseEntity<ApiResponse<RoleResponse>> getRoleByName(@PathVariable String name) {
            Role role = List.of(Role.values()).stream()
                    .filter(r -> r.name().equalsIgnoreCase(name))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Role not found: " + name));

            long userCount = userRepository.countByRole(role);
            RoleResponse response = RoleResponse.fromRole(role, userCount, role.getPermissions().size());

            return ResponseEntity.ok(ApiResponse.success(response, "Role retrieved successfully"));
        }
}
