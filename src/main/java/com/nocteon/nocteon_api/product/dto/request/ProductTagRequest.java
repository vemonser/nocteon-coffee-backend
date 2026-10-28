package com.nocteon.nocteon_api.product.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Create and rename share one body: the page edits translated names only, because the slug is the
 * identity the product form and the public filter already point at.
 */
@Getter
@Setter
public class ProductTagRequest {

    @NotEmpty(message = "{validation.translations.notEmpty}")
    @Size(min = 2, message = "{validation.translations.size}")
    private List<@Valid ProductTagTranslationRequest> translations;
}
