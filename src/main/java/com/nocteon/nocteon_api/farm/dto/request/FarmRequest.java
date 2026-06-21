package com.nocteon.nocteon_api.farm.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class FarmRequest {
    

    @NotBlank(message = "{validation.origin.notBlank}")
    @Size(min = 2, max = 100, message = "{validation.origin.size}")
    private String originSlug;   

    @NotEmpty(message = "{validation.translations.notBlank}")
    @Size(min = 2, message = "{validation.translations.size}")
    private List<@Valid FarmTranslationRequest> translations;

}
