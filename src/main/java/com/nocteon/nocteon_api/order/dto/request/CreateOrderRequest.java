package com.nocteon.nocteon_api.order.dto.request;

import com.nocteon.nocteon_api.payment.enums.PaymentMethod;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateOrderRequest {

    @NotNull(message = "{validation.address.notNull}")
    private Long addressId;

    @NotNull(message = "{validation.paymentMethod.notNull}")
    private PaymentMethod paymentMethod;

    private String promoCode; 

    @Size(max = 500, message = "{validation.notes.size}")
    private String notes;
}