package com.nocteon.nocteon_api.product.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductBrewingMethodRequest {

    @NotBlank(message = "{validation.brewingMethod.notBlank}")
    private String brewingMethodSlug;

    @Min(value = 0, message = "{validation.score.min}")
    @Max(value = 5, message = "{validation.score.max}")
    private int score;
}