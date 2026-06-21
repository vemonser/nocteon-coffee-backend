package com.nocteon.nocteon_api.common.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ApiFieldError {
    private String field;
    private String message;
}