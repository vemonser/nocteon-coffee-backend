package com.nocteon.nocteon_api.brewingMethod.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class BrewingMethodResponse {
    private Long id;
    private String slug;
    private String name;
    private String description;

}
