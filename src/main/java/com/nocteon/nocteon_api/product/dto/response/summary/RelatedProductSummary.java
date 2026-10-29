package com.nocteon.nocteon_api.product.dto.response.summary;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * A related product as the admin picks it: enough to render the chip (name and thumbnail) and
 * enough to save it back (the slug).
 */
@Getter
@Setter
@Builder
public class RelatedProductSummary {
    private String slug;
    private String name;
    private String primaryImageUrl;
}
