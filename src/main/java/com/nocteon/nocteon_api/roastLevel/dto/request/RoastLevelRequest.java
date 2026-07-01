package com.nocteon.nocteon_api.roastLevel.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoastLevelRequest {
    
    @NotBlank(message = "{validation.color.notBlank}")
    @Size(min = 6, max = 8,message = "{validation.color.size}")
    private String color;
    @NotEmpty(message = "{validation.translations.notEmpty}")
    @Size(min = 2, message = "{validation.translations.size}")
    private List<@Valid RoastLevelTranslationRequest> translations;

}
