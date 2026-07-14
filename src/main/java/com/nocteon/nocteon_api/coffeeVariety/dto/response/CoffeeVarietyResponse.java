package com.nocteon.nocteon_api.coffeeVariety.dto.response;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CoffeeVarietyResponse {
    private Long id;
    private String slug;
    private Instant createdAt;
    private String name;
    private String description;
}