package com.nocteon.nocteon_api.product.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nocteon.nocteon_api.product.dto.response.summary.ProductTagSummary;
import com.nocteon.nocteon_api.product.entity.ProductTag;
import com.nocteon.nocteon_api.product.entity.ProductTagTranslation;

/**
 * FE-10: what a product's tags look like on the wire.
 *
 * <p>Both the dashboard and the public product read this shape, and the storefront builds its
 * filter from the slug — so the slug is always present, the name is always in the caller's
 * language when there is one, and an untranslated tag still renders as something.
 */
class ProductTagMapperTest {

    private final ProductTagMapper mapper = new ProductTagMapper();

    @Test
    @DisplayName("names are resolved in the requested language")
    void resolvesTheRequestedLanguage() {
        List<ProductTagSummary> summaries = mapper.toSummaries(
                List.of(tag("new-arrivals", "New arrivals", "وصل حديثاً")), "ar");

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).getSlug()).isEqualTo("new-arrivals");
        assertThat(summaries.get(0).getName()).isEqualTo("وصل حديثاً");
    }

    @Test
    @DisplayName("a tag without that language falls back to English, then to its slug")
    void fallsBackToEnglishThenToTheSlug() {
        ProductTag englishOnly = tag("gift-boxes", "Gift boxes", null);
        ProductTag bare = ProductTag.builder().slug("bare").build();

        List<ProductTagSummary> summaries = mapper.toSummaries(List.of(englishOnly, bare), "ar");

        assertThat(summaries).extracting(ProductTagSummary::getName)
                .containsExactly("bare", "Gift boxes");
    }

    @Test
    @DisplayName("tags come back in a stable order so the chips do not jump")
    void ordersBySlug() {
        List<ProductTagSummary> summaries = mapper.toSummaries(
                List.of(tag("zebra", "Zebra", "زيبرا"), tag("alpha", "Alpha", "ألفا")), "en");

        assertThat(summaries).extracting(ProductTagSummary::getSlug)
                .containsExactly("alpha", "zebra");
    }

    private static ProductTag tag(String slug, String en, String ar) {
        ProductTag tag = ProductTag.builder().slug(slug).translations(new ArrayList<>()).build();
        tag.getTranslations().add(ProductTagTranslation.builder()
                .tag(tag).language("en").name(en).build());
        if (ar != null) {
            tag.getTranslations().add(ProductTagTranslation.builder()
                    .tag(tag).language("ar").name(ar).build());
        }
        return tag;
    }
}
