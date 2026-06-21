package com.nocteon.nocteon_api.common.dto;

import java.time.Instant;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private List<ApiFieldError> errors;
    private Instant timestamp;

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> error(String message, List<ApiFieldError> errors) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .errors(errors)
                .timestamp(Instant.now())
                .build();
    }
}