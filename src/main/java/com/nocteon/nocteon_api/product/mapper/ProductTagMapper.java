package com.nocteon.nocteon_api.product.mapper;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.nocteon.nocteon_api.product.dto.response.summary.ProductTagSummary;
import com.nocteon.nocteon_api.product.entity.ProductTag;
import com.nocteon.nocteon_api.product.entity.ProductTagTranslation;

/**
 * Turns a product's tags into the two-field shape both product responses carry.
 *
 * <p>The name is resolved in the caller's language with an English fallback and the slug as the
 * last resort, so a tag that was created before a language existed still renders as something the
 * storefront can filter on.
 */
@Component
public class ProductTagMapper {

    public List<ProductTagSummary> toSummaries(Collection<ProductTag> tags, String language) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
                .sorted(Comparator.comparing(ProductTag::getSlug))
                .map(tag -> ProductTagSummary.builder()
                        .slug(tag.getSlug())
                        .name(nameOf(tag, language))
                        .build())
                .toList();
    }

    private static String nameOf(ProductTag tag, String language) {
        return tag.getTranslations().stream()
                .filter(t -> t.getLanguage().equals(language))
                .map(ProductTagTranslation::getName)
                .findFirst()
                .or(() -> tag.getTranslations().stream()
                        .filter(t -> t.getLanguage().equals("en"))
                        .map(ProductTagTranslation::getName)
                        .findFirst())
                .orElse(tag.getSlug());
    }
}
