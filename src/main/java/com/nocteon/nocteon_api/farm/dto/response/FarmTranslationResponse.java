package com.nocteon.nocteon_api.farm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Both constructor annotations are load-bearing, not decoration. {@code @Builder} synthesises an
 * all-args package-private constructor, and a class with any explicit constructor does not get the
 * implicit no-args one. The trashed-farm listing fills this type field by field after
 * {@code new FarmTranslationResponse()}, so without {@code @NoArgsConstructor} that call does not
 * compile; {@code @AllArgsConstructor} states the builder's shape explicitly so the pair stays
 * unambiguous.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarmTranslationResponse {
    private String language;
    private String name;
    private String description;
    private String country;

}
