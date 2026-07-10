package com.nocteon.nocteon_api.dashboard.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TopSellingProductDto {
    private Long id;
    private String slug;
    private String name;
    private String imageUrl;
    private BigDecimal price;
    private long quantitySold;
}