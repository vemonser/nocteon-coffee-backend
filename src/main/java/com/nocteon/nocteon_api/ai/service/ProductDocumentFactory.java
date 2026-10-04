package com.nocteon.nocteon_api.ai.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethod;
import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.coffeeVariety.entity.CoffeeVariety;
import com.nocteon.nocteon_api.farm.entity.Farm;
import com.nocteon.nocteon_api.origin.entity.Origin;
import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethod;
import com.nocteon.nocteon_api.product.entity.CoffeeDetails;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductBrewingMethod;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevel;
import com.nocteon.nocteon_api.tastingNote.entity.TastingNote;

/**
 * Projects a {@link Product} into the text and metadata that get embedded for
 * semantic search.
 *
 * <p>Pure projection with no collaborators and no I/O, so every branch is
 * unit-testable without a Spring context.
 *
 * <p>Two rules hold everywhere here:
 * <ul>
 * <li>{@code coffeeDetails} is {@code null} for every non-coffee product type, so it is
 * never dereferenced unchecked — a merchandise product still yields usable text from its
 * translations and category instead of being dropped or throwing.</li>
 * <li>No language is assumed to exist. Translations are iterated and labelled by their
 * own {@code language} value, so a product carrying only {@code en}, or a third language
 * added later, is indexed without a code change.</li>
 * </ul>
 *
 * <p>Related entities contribute their {@code slug} rather than a translated name: slugs
 * are stable and ASCII, which keeps the embedded text (and therefore
 * {@code contentHash}) identical across locales.
 */
@Component
public class ProductDocumentFactory {

    /** Prefix that marks a vector-store document as belonging to a product. */
    private static final String DOCUMENT_ID_PREFIX = "product:";

    /**
     * Builds the vector-store document for a product.
     *
     * @param product the product to project, never {@code null}
     * @return a document id'd {@code product:<slug>} with the built text and metadata
     */
    public Document toDocument(Product product) {
        return new Document(DOCUMENT_ID_PREFIX + product.getSlug(),
                buildText(product),
                buildMetadata(product));
    }

    /**
     * Builds the embeddable text: newline-separated sections, skipping anything absent.
     *
     * @param product the product to project, never {@code null}
     * @return human-readable text, never {@code null}, possibly empty for a bare product
     */
    public String buildText(Product product) {
        StringBuilder text = new StringBuilder();
        List<ProductTranslation> translations = sortedTranslations(product);

        for (ProductTranslation translation : translations) {
            appendLine(text, nameLabel(translation), translation.getName());
        }

        appendRelation(text, "Category", product.getCategory(), Category::getSlug);
        appendRelation(text, "Origin", product.getOrigin(), Origin::getSlug);
        appendRelation(text, "Farm", product.getFarm(), Farm::getSlug);
        appendCoffeeDetails(text, product.getCoffeeDetails());

        appendLine(text, "Tasting Notes",
                String.join(", ", slugs(product.getTastingNotes(), TastingNote::getSlug)));
        appendLine(text, "Brewing Methods",
                String.join(", ", brewingLabels(product.getBrewingMethods())));

        for (ProductTranslation translation : translations) {
            appendLine(text, descriptionLabel(translation), translation.getShortDescription());
        }

        return text.toString();
    }

    /**
     * Builds the vector-store metadata for a product.
     *
     * <p>{@code categorySlug} and {@code originSlug} are omitted when the corresponding
     * relation is absent, so callers filtering on them never match an unrelated document.
     *
     * @param product the product to project, never {@code null}
     * @return a mutable insertion-ordered map
     */
    public Map<String, Object> buildMetadata(Product product) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("productId", product.getId());
        metadata.put("slug", product.getSlug());
        putSlug(metadata, "categorySlug", product.getCategory(), Category::getSlug);
        putSlug(metadata, "originSlug", product.getOrigin(), Origin::getSlug);
        metadata.put("contentHash", sha256Hex(buildText(product)));
        return metadata;
    }

    private void appendCoffeeDetails(StringBuilder text, CoffeeDetails details) {
        // Null for every non-coffee product type — never dereferenced unchecked.
        if (details == null) {
            return;
        }
        appendRelation(text, "Roast Level", details.getRoastLevel(), RoastLevel::getSlug);
        appendRelation(text, "Processing Method", details.getProcessingMethod(), ProcessingMethod::getSlug);
        appendRelation(text, "Coffee Variety", details.getCoffeeVariety(), CoffeeVariety::getSlug);
    }

    private void appendLine(StringBuilder text, String label, String value) {
        if (StringUtils.hasText(value)) {
            text.append(label).append(": ").append(value.trim()).append('\n');
        }
    }

    private <T> void appendRelation(StringBuilder text, String label, T entity, Function<T, String> slugGetter) {
        if (entity != null) {
            appendLine(text, label, slugGetter.apply(entity));
        }
    }

    private <T> void putSlug(Map<String, Object> metadata, String key, T entity, Function<T, String> slugGetter) {
        if (entity == null) {
            return;
        }
        String slug = slugGetter.apply(entity);
        if (StringUtils.hasText(slug)) {
            metadata.put(key, slug);
        }
    }

    /**
     * Translations sorted so the built text — and therefore {@code contentHash} — does not
     * depend on set iteration order, which is arbitrary for the entity's {@code HashSet}.
     */
    private List<ProductTranslation> sortedTranslations(Product product) {
        return product.getTranslations().stream()
                .sorted(Comparator
                        .comparing((ProductTranslation t) -> nullSafe(t.getLanguage()))
                        .thenComparing(t -> nullSafe(t.getName())))
                .toList();
    }

    private <T> List<String> slugs(List<T> entities, Function<T, String> slugGetter) {
        return entities.stream()
                .map(slugGetter)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
    }

    /** Brewing methods as {@code slug:score} pairs, sorted for a stable text projection. */
    private List<String> brewingLabels(List<ProductBrewingMethod> brewingMethods) {
        return brewingMethods.stream()
                .filter(brewingMethod -> brewingMethod.getBrewingMethod() != null)
                .filter(brewingMethod -> StringUtils.hasText(brewingMethod.getBrewingMethod().getSlug()))
                .map(brewingMethod -> brewingMethod.getBrewingMethod().getSlug().trim()
                        + ":" + brewingMethod.getScore())
                .sorted()
                .toList();
    }

    private String nameLabel(ProductTranslation translation) {
        return StringUtils.hasText(translation.getLanguage())
                ? "Name (" + translation.getLanguage().trim() + ")"
                : "Name";
    }

    private String descriptionLabel(ProductTranslation translation) {
        return StringUtils.hasText(translation.getLanguage())
                ? "Description (" + translation.getLanguage().trim() + ")"
                : "Description";
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform but was unavailable", e);
        }
    }
}