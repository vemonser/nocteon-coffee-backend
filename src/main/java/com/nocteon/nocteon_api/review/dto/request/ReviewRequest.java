package com.nocteon.nocteon_api.review.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewRequest {

    @NotBlank(message = "{validation.product.notBlank}")
    private String productSlug;

    @NotNull(message = "{validation.rating.notNull}")
    @Min(value = 1, message = "{validation.rating.min}")
    @Max(value = 5, message = "{validation.rating.max}")
    private Integer rating;

    @Size(max = 1000, message = "{validation.comment.size}")
    private String comment;
}