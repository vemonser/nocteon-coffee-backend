package com.nocteon.nocteon_api.product.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.nocteon.nocteon_api.product.dto.response.summary.LockupResponseImage;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.review.dto.response.ReviewResponse;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ProductResponse {
    private Long id;
    private String slug;
    private LockupResponseImage category; 
    private LockupResponseImage origin; 
    private LockupResponseImage farm; 
    private ProductType productType;
    private boolean featured;
    private boolean isActive;

    // من الـ translation
    private String name;
    private String shortDescription;
    private String description;

    // للـ List Response بس
    private String primaryImageUrl;
    private BigDecimal lowestPrice;

    private Double averageRating;
    private Long reviewCount;
    private List<ReviewResponse> recentReviews;

    // Relations
    private CoffeeDetailsResponse coffeeDetails;
    private List<ProductVariantResponse> variants;
    private List<String> tastingNotes;
    private List<ProductPairingResponse> pairings;
    private List<ProductBrewingMethodResponse> brewingMethods;
    private List<ProductMediaResponse> media;
}
