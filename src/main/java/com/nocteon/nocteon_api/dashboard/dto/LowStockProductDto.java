package com.nocteon.nocteon_api.dashboard.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LowStockProductDto {
    private Long id;
    private String slug;
    private String name;
    private String imageUrl;
    private int stockQuantity;
}
