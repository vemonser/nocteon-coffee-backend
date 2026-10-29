package com.nocteon.nocteon_api.auth.dto.response;

import com.nocteon.nocteon_api.auth.enums.Role;
import com.nocteon.nocteon_api.common.enums.Permission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleResponse {

    private String name;

    private String description;

    private boolean system;

    private long userCount;

    private long permissionCount;

    private List<String> permissions; // slug format: "resource:action"

    /** Permission diff returned by PUT /api/dashboard/roles/{name}. Null otherwise. */
    private List<String> granted;
    private List<String> revoked;
    private List<String> sensitiveGranted;

    public static RoleResponse fromRole(Role role, long userCount, long permissionCount) {
        RoleResponse response = new RoleResponse();
        response.name = role.name();
        response.description = role.name(); // default to role name
        response.system = true; // all current roles are system roles
        response.userCount = userCount;
        response.permissionCount = permissionCount;
        response.permissions = role.getPermissions().stream()
                .map(Permission::getPermission)
                .collect(Collectors.toList());
        return response;
    }
}