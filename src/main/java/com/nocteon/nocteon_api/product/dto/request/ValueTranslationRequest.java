package com.nocteon.nocteon_api.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ValueTranslationRequest {

    @NotBlank(message = "{validation.language.notBlank}")
    private String language;

    @NotBlank(message = "{validation.value.notBlank}")
    private String value;
}