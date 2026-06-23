package com.nocteon.nocteon_api.wishlist.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class WishlistResponse {
    private Long id;
    private List<WishlistItemResponse> items;
    private int itemCount;
}