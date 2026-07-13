package com.nocteon.nocteon_api.product.dto.response;


import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductWithScoreResponse {
    private ProductCardResponse product;
    private int score;
}