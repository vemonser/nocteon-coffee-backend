package com.nocteon.nocteon_api.product.dto.response;

import java.time.Instant;
import java.util.List;

import com.nocteon.nocteon_api.journal.dto.response.TranslationResponseDashboard;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/** A tag as the admin page sees it: identity, creation time and every translated name. */
@Getter
@Setter
@Builder
public class ProductTagResponseDashboard {
    private Long id;
    private String slug;
    private Instant createdAt;
    private List<TranslationResponseDashboard> translations;
}
