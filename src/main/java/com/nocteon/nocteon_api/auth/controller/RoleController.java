package com.nocteon.nocteon_api.auth.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nocteon.nocteon_api.auth.dto.request.CreateRoleRequest;
import com.nocteon.nocteon_api.auth.dto.request.UpdateRoleRequest;
import com.nocteon.nocteon_api.auth.dto.response.RoleResponse;
import com.nocteon.nocteon_api.auth.service.RoleService;
import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.enums.Permission;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority('user:read') or hasAuthority('role:read')")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAll() {
        List<RoleResponse> responses = roleService.getAllRoles().stream()
                .map(r -> toResponse(r))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses, "Roles retrieved successfully"));
    }

    @GetMapping("/{name}")
    @PreAuthorize("hasAuthority('user:read') or hasAuthority('role:read')")
    public ResponseEntity<ApiResponse<RoleResponse>> getByName(@PathVariable String name) {
        RoleResponse response = toResponse(roleService.getRole(name));
        return ResponseEntity.ok(ApiResponse.success(response, "Role retrieved successfully"));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('role:manage')")
    public ResponseEntity<ApiResponse<RoleResponse>> create(@RequestBody CreateRoleRequest request) {
        var role = roleService.create(request.getName(), request.getDescription(), request.getCloneFrom());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(toResponse(role), "Role created successfully"));
    }

    @PutMapping("/{name}")
    @PreAuthorize("hasAuthority('role:manage')")
    public ResponseEntity<ApiResponse<RoleResponse>> update(@PathVariable String name, @RequestBody UpdateRoleRequest request) {
        var role = roleService.update(name, request.getDescription(), request.getPermissions(), null);
        return ResponseEntity.ok(ApiResponse.success(toResponse(role), "Role updated successfully"));
    }

    @DeleteMapping("/{name}")
    @PreAuthorize("hasAuthority('role:manage')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String name) {
        roleService.delete(name);
        return ResponseEntity.ok(ApiResponse.success(null, "Role deleted successfully"));
    }

    private RoleResponse toResponse(com.nocteon.nocteon_api.auth.entity.RoleEntity r) {
        RoleResponse resp = new RoleResponse();
        resp.setName(r.getName());
        resp.setDescription(r.getDescription());
        resp.setSystem(r.isSystem());
        resp.setUserCount(userCount(r.getName()));
        resp.setPermissionCount(r.getPermissions().size());
        resp.setPermissions(new java.util.ArrayList<>(r.getPermissions()));
        return resp;
    }

    private long userCount(String name) {
        return roleService.userCount(name);
    }
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('user:read') or hasAuthority('role:read')")
    public ResponseEntity<ApiResponse<List<java.util.Map<String, String>>>> getPermissions() {
        List<java.util.Map<String, String>> list = java.util.Arrays.stream(Permission.values())
                .map(p -> {
                    String slug = p.getPermission();
                    String[] parts = slug.split(":", 2);
                    return java.util.Map.of("slug", slug, "resource", parts[0], "action", parts.length > 1 ? parts[1] : "");
                })
                .toList();
        return ResponseEntity.ok(ApiResponse.success(list, "Permissions retrieved successfully"));
    }

}
