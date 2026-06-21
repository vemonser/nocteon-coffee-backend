package com.nocteon.nocteon_api.origin.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class OriginRequest {

    @NotBlank(message = "{validation.code.notBlank}")
    @Size(min = 2, max = 10, message = "{validation.code.size}")
    private String code;   

    @NotEmpty(message = "{validation.translations.notBlank}")
    @Size(min = 2, message = "{validation.translations.size}")
    private List<@Valid OriginTranslationRequest> translations;

}
