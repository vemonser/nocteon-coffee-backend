package com.nocteon.nocteon_api.common.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LookupFilterRequest extends BaseFilterRequest {
    private String search;
    private Boolean isActive;
}