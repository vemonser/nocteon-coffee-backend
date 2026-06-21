package com.nocteon.nocteon_api.coffeeVariety.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CoffeeVarietyResponse {
    private Long id;
    private String slug;
    private String name;
    private String description;
}