package com.nocteon.nocteon_api.wishlist.dto.response;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class WishlistItemResponse {
    private Long id;
    private String productSlug;
    private String productName;
    private String primaryImageUrl;
    private BigDecimal lowestPrice;
}