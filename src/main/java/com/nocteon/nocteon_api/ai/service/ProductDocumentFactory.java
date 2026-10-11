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

import com.nocteon.nocteon_api.ai.config.EmbeddingModelFingerprint;
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
 * <p>Pure projection with no I/O and a single inert collaborator, so every branch is
 * unit-testable without a Spring context.
 *
 * <h2>Two things are layered on top of the projection</h2>
 * <ul>
 * <li>{@link EmbeddingTaskPrefix#DOCUMENT}, the document-side retrieval envelope. Applied in
 * {@link #buildText(Product)} because that is the only side this class owns; the query side is
 * {@link EmbeddingTaskPrefix#QUERY}, applied by {@code AiSearchService}.</li>
 * <li>{@code embeddingModel}, the identity of the configured embedding model, mixed into
 * {@code contentHash} so a model swap invalidates every stored vector instead of leaving a
 * silently-wrong index behind.</li>
 * </ul>
 *
 * <p>Neither changes what is projected: every field below is read and written exactly as it was
 * before, which is why the per-field assertions are unchanged.
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

    /** Metadata key naming the embedding model this document's vector was produced by. */
    private static final String EMBEDDING_MODEL_KEY = "embeddingModel";

    private final EmbeddingModelFingerprint embeddingModel;

    /**
     * @param embeddingModel the configured embedding model's identity; mixed into
     *                       {@code contentHash} so a model change forces a re-embed rather than
     *                       leaving vectors from a different embedding space in the store
     */
    public ProductDocumentFactory(EmbeddingModelFingerprint embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * Builds the vector-store document for a product.
     *
     * <p>The id is a deterministic UUID, not {@code "product:<slug>"}: Spring AI 2.0.1's
     * {@code PgVectorStore} parses document ids with {@link java.util.UUID#fromString(String)}
     * before writing them, so a human-readable id is rejected on the first write. The slug lives in
     * the metadata instead, and {@link ProductDocumentIds} owns the mapping.
     *
     * @param product the product to project, never {@code null}
     * @return a document carrying the built text and metadata, id'd {@link ProductDocumentIds#forSlug(String)}
     */
    public Document toDocument(Product product) {
        return new Document(ProductDocumentIds.forSlug(product.getSlug()),
                buildText(product),
                buildMetadata(product));
    }

    /**
     * Builds the embeddable text: the document-side task prefix, then newline-separated sections,
     * skipping anything absent.
     *
     * <p>The prefix is applied here, on the document side, because that is the only side this
     * class owns. The query side is applied in {@code AiSearchService} via the same
     * {@link EmbeddingTaskPrefix} constants, so the two cannot drift apart — see that class for
     * why the intent lives in the text at all. Note that putting the prefix <em>inside</em> the
     * built text rather than in {@link #toDocument(Product)} is what keeps
     * {@code SimilarProductService} consistent for free: it passes this same text as its query, so
     * it compares document-formatted against document-formatted instead of striding two regions
     * of the embedding space.
     *
     * <p>What the prefix does <em>not</em> change is what is projected. Every field below is
     * projected exactly as before; the prefix is a few tokens of retrieval envelope in front of
     * them, which is why the existing per-field assertions still hold unchanged.
     *
     * @param product the product to project, never {@code null}
     * @return human-readable text, never {@code null}, possibly empty for a bare product
     */
    public String buildText(Product product) {
        return EmbeddingTaskPrefix.forDocument(projectText(product));
    }

    /**
     * The projected product text, without the document-side task prefix.
     *
     * <p>Kept separate from {@link #buildText(Product)} so the projection itself is testable and
     * readable on its own, and so the prefix has exactly one call site.
     */
    private String projectText(Product product) {
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
     * <p>{@code productId} and {@code slug} are the document's only route back to the product, now
     * that the id is an opaque UUID — every consumer resolves identity from here, never from the id.
     *
     * <p>{@code categorySlug} and {@code originSlug} are omitted when the corresponding
     * relation is absent, so callers filtering on them never match an unrelated document.
     *
     * <p>{@code embeddingModel} is stored so the model that produced a stored vector is
     * readable, and it is folded into {@code contentHash} so {@code ProductIndexingService}'s
     * existing staleness comparison does the re-embed automatically when the model changes. Two
     * models that both emit 768 dimensions are not interchangeable, and nothing else in the stored
     * row would reveal the swap — see {@link EmbeddingModelFingerprint}.
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
        metadata.put(EMBEDDING_MODEL_KEY, embeddingModel.value());
        metadata.put("contentHash", sha256Hex(embeddingModel.value() + '\n' + buildText(product)));
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