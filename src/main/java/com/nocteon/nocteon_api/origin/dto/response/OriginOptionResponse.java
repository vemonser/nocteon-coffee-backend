package com.nocteon.nocteon_api.origin.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OriginOptionResponse {
    private String slug;
    private String name;
}