package com.nocteon.nocteon_api.ai.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.StringUtils;

import com.nocteon.nocteon_api.coffeeVariety.entity.CoffeeVariety;
import com.nocteon.nocteon_api.coffeeVariety.entity.CoffeeVarietyTranslation;
import com.nocteon.nocteon_api.farm.entity.Farm;
import com.nocteon.nocteon_api.farm.entity.FarmTranslation;
import com.nocteon.nocteon_api.origin.entity.Origin;
import com.nocteon.nocteon_api.origin.entity.OriginTranslation;
import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethod;
import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethodTranslation;
import com.nocteon.nocteon_api.product.entity.CoffeeDetails;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevel;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevelTranslation;
import com.nocteon.nocteon_api.tastingNote.entity.TastingNoteTranslation;

import lombok.RequiredArgsConstructor;

/**
 * The authoritative catalog facts about one coffee, as the string the model is given.
 *
 * <h2>Why every value in here comes from a column</h2>
 * This class is the answer to a language model's willingness to invent a plausible origin, a
 * plausible tasting note and a plausible price for a coffee that does not exist. Nothing in the
 * returned string is derived from the model, and a field the catalog does not hold is
 * <em>omitted</em> rather than filled with a placeholder — a line reading {@code "Variety:
 * unknown"} is one the model is likely to complete with a variety that does exist.
 *
 * <h2>Why the read runs through {@link TransactionOperations}</h2>
 * {@code findBySlug} join-fetches nothing, so walking {@code translations}, {@code variants},
 * {@code coffeeDetails} and {@code tastingNotes} needs an open persistence context. It is opened
 * around the read and the assembly together — the assembly <em>is</em> part of the read, since
 * every getter it calls is a lazy load.
 *
 * <p>A transaction callback rather than {@code @Transactional} because the caller reaches this
 * class as an injected singleton from a tool method that Spring AI invokes reflectively on a raw
 * instance, not through a proxy, where an annotation would silently do nothing.
 *
 * <h2>Why this throws and {@code get_product_details} does not</h2>
 * The never-throw contract belongs to the tool, not to the read: a failure here has to become the
 * "report nothing" instruction, and only the tool knows that instruction. Letting this class throw
 * keeps the failure visible instead of swallowing it into a fact sheet.
 */
@Component
@RequiredArgsConstructor
public class CoffeeFactSheet {

    /** Separator between facts on one line; fixed so a small model can parse the shape. */
    private static final String FACT_SEPARATOR = " | ";

    private static final String LIST_SEPARATOR = ", ";

    /**
     * The instruction returned when the catalog holds nothing for the slug, or the read failed.
     *
     * <p>Package-private rather than private so tests can assert the tool returns <em>this</em>
     * value rather than merely something non-blank — the wording is the contract.
     */
    static final String UNKNOWN_PRODUCT =
            "No coffee in the catalog matches that. Report no match; do not suggest a coffee anyway.";

    /**
     * The instruction returned for a coffee that exists but is not on sale.
     *
     * <p>Distinct from {@link #UNKNOWN_PRODUCT} on purpose: a delisted coffee must not be
     * described from memory either, but the model has to report it as <em>unavailable</em>
     * rather than as never having existed.
     */
    static final String INACTIVE_PRODUCT =
            "That coffee is not currently for sale. Report it as unavailable; do not describe it.";

    private final ProductRepository productRepository;
    private final TransactionOperations transactions;

    /**
     * The fact sheet for one slug, or the instruction to report nothing.
     *
     * <p>Must be called with a persistence context open — this method opens one itself.
     *
     * @param slug the slug {@code search_products} returned, already trimmed
     * @return the fact sheet, or one of the two report-nothing instructions
     */
    public String factsFor(String slug) {
        return inTransaction(() -> lookupForFacts(slug));
    }

    /**
     * Fetches one product for the fact sheet.
     *
     * <p>Must be called with a persistence context open: {@code findBySlug} join-fetches nothing.
     */
    private String lookupForFacts(String slug) {
        Optional<Product> found = productRepository.findBySlug(slug);
        if (found.isEmpty()) {
            return UNKNOWN_PRODUCT;
        }
        if (!found.get().isActive()) {
            return INACTIVE_PRODUCT;
        }
        return describe(found.get());
    }

    private String describe(Product product) {
        String language = TranslatedNames.currentLanguage();

        StringBuilder facts = new StringBuilder(name(product, language))
                .append(" (slug: ")
                .append(product.getSlug())
                .append(')');

        String attributes = attributes(product, language);
        if (!attributes.isEmpty()) {
            facts.append('\n').append(attributes);
        }

        String notes = tastingNotes(product, language);
        if (!notes.isEmpty()) {
            facts.append("\nTasting notes: ").append(notes);
        }

        return facts.append('\n')
                .append("Price: ").append(price(product))
                .append(FACT_SEPARATOR)
                .append("Availability: ").append(availability(product))
                .toString();
    }

    private String attributes(Product product, String language) {
        List<String> attributes = new ArrayList<>();

        add(attributes, "Origin", product.getOrigin(), language,
                Origin::getTranslations, OriginTranslation::getName);
        add(attributes, "Farm", product.getFarm(), language,
                Farm::getTranslations, FarmTranslation::getName);

        CoffeeDetails details = product.getCoffeeDetails();
        if (details != null) {
            add(attributes, "Roast", details.getRoastLevel(), language,
                    RoastLevel::getTranslations, RoastLevelTranslation::getName);
            add(attributes, "Process", details.getProcessingMethod(), language,
                    ProcessingMethod::getTranslations, ProcessingMethodTranslation::getName);
            add(attributes, "Variety", details.getCoffeeVariety(), language,
                    CoffeeVariety::getTranslations, CoffeeVarietyTranslation::getName);
        }

        return String.join(FACT_SEPARATOR, attributes);
    }

    /**
     * Appends one {@code "Label: value"} attribute, skipping anything the catalog does not hold.
     *
     * <p>Omitted rather than filled with a placeholder: a line reading {@code "Variety: unknown"}
     * is one the model is likely to complete with a variety that does exist.
     */
    private <R, T> void add(List<String> target, String label, R relation, String language,
            Function<R, List<T>> translations, Function<T, String> nameOf) {

        if (relation == null) {
            return;
        }
        String translated = TranslatedNames.nameIn(translations.apply(relation), language, nameOf);
        if (StringUtils.hasText(translated)) {
            target.add(label + ": " + translated);
        }
    }

    /**
     * Falls back to the slug only when no translation carries a name at all, which is the one case
     * where the alternative is a blank first token on the fact sheet.
     */
    private String name(Product product, String language) {
        String translated = TranslatedNames.nameIn(
                List.copyOf(product.getTranslations()), language, ProductTranslation::getName);

        return StringUtils.hasText(translated) ? translated : product.getSlug();
    }

    private String tastingNotes(Product product, String language) {
        return product.getTastingNotes().stream()
                .filter(note -> note.getTranslations() != null)
                .map(note -> TranslatedNames.nameIn(note.getTranslations(), language,
                        TastingNoteTranslation::getName))
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.joining(LIST_SEPARATOR));
    }

    /**
     * The price range over variants that are actually buyable.
     *
     * <p>A retired variant's price is never quoted even when it is the highest: quoting a price
     * the customer cannot pay is the single most actionable way this fact sheet could mislead.
     *
     * @return a {@code min - max} range, a single figure when every active variant costs the
     *         same, or {@code "unknown"} when nothing is on sale
     */
    private String price(Product product) {
        List<BigDecimal> prices = product.getVariants().stream()
                .filter(variant -> variant.isActive() && variant.getPrice() != null)
                .map(ProductVariant::getPrice)
                .toList();
        if (prices.isEmpty()) {
            return "unknown";
        }

        BigDecimal min = prices.stream().min(Comparator.naturalOrder()).orElseThrow();
        BigDecimal max = prices.stream().max(Comparator.naturalOrder()).orElseThrow();

        return min.compareTo(max) == 0
                ? min.toPlainString()
                : min.toPlainString() + " - " + max.toPlainString();
    }

    private String availability(Product product) {
        boolean inStock = product.getVariants().stream()
                .anyMatch(variant -> variant.isActive() && variant.getStockQuantity() > 0);
        return inStock ? "in stock" : "currently unavailable";
    }

    /** Runs the catalog read with a persistence context open, and nothing else. */
    private <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }
}