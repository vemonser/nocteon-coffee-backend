package com.nocteon.nocteon_api.product.dto.response.summary;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * A tag on a product. The slug drives the public filter (so the storefront can build
 * {@code ?tagSlug=}) and the name is already resolved in the request's language.
 */
@Getter
@Setter
@Builder
public class ProductTagSummary {
    private String slug;
    private String name;
}
