package com.nocteon.nocteon_api.category.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryRequest {
    private Boolean isActive;
    @NotEmpty(message = "{validation.translations.notBlank}")
    @Size(min = 2, message = "{validation.translations.size}")
    private List<@Valid CreateCategoryTranslationRequest> translations;
}
