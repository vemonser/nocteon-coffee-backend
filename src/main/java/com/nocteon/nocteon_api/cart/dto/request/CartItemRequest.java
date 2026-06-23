package com.nocteon.nocteon_api.cart.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CartItemRequest {

    @NotNull(message = "{validation.variant.notNull}")
    private Long variantId;

    @Min(value = 1, message = "{validation.quantity.min}")
    private int quantity = 1;
}
