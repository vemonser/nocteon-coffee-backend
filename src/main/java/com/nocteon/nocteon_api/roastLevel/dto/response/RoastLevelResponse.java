package com.nocteon.nocteon_api.roastLevel.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RoastLevelResponse {
    private Long id;
    private String slug;
    private String color;
    private String name;
    private String description;

}
