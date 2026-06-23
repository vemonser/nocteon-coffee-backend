package com.nocteon.nocteon_api.address.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddressRequest {

    @NotBlank(message = "{validation.fullName.notBlank}")
    private String fullName;

    @NotBlank(message = "{validation.phone.notBlank}")
    private String phone;

    @NotBlank(message = "{validation.street.notBlank}")
    private String street;

    @NotBlank(message = "{validation.city.notBlank}")
    private String city;

    private String state;

    @NotBlank(message = "{validation.country.notBlank}")
    private String country;

    private String postalCode;

    private boolean isDefault = false;
}