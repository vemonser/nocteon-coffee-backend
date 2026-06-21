package com.nocteon.nocteon_api.roastProfile.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RoastProfileResponse {
    private Long id;
    private String slug;
    private String name;
    private String description;

}
