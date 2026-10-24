package com.nocteon.nocteon_api.auth.dto.request;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class UpdateRoleRequest {
    private String description;
    private List<String> permissions;
}
