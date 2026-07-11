package com.nocteon.nocteon_api.auth.dto.request;



import com.nocteon.nocteon_api.auth.enums.Role;
import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserFilterRequest extends LookupFilterRequest {
    private Role role;
    private Boolean enabled;
}
