package com.nocteon.nocteon_api.farm.dto.response;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class FarmResponse {
    private Long id;
    private String originSlug;
    private String slug;
    private String name;
    private String country;  
    private String description;
    private String imageUrl;
    private Instant createdAt;
    
}