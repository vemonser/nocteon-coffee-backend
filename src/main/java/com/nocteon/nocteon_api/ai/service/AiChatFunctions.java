package com.nocteon.nocteon_api.ai.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.StringUtils;

import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethod;
import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethodTranslation;
import com.nocteon.nocteon_api.brewingMethod.repository.BrewingMethodRepository;
import com.nocteon.nocteon_api.coffeeVariety.entity.CoffeeVariety;
import com.nocteon.nocteon_api.coffeeVariety.entity.CoffeeVarietyTranslation;
import com.nocteon.nocteon_api.farm.entity.Farm;
import com.nocteon.nocteon_api.farm.entity.FarmTranslation;
import com.nocteon.nocteon_api.origin.entity.Origin;
import com.nocteon.nocteon_api.origin.entity.OriginTranslation;
import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethod;
import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethodTranslation;
import com.nocteon.nocteon_api.product.dto.response.ProductCardResponse;
import com.nocteon.nocteon_api.product.entity.CoffeeDetails;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.mapper.ProductResponseMapper;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevel;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevelTranslation;
import com.nocteon.nocteon_api.tastingNote.entity.TastingNote;
import com.nocteon.nocteon_api.tastingNote.entity.TastingNoteTranslation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The three tools the assistant is allowed to call, and the only way a catalog fact reaches it.
 *
 * <h2>Why this class is the hallucination guardrail</h2>
 * A language model will happily produce a plausible origin, a plausible tasting note and a
 * plausible price for a coffee that does not exist. No system prompt reliably prevents that;
 * what prevents it is that there is <em>nowhere else for facts to come from</em>. Every name,
 * number and availability flag below is read from the database and handed to the model as a
 * finished fact, so a wrong price cannot be written into a reply — the model can only fail to
 * mention one.
 *
 * <p>It is also why there are no cart, order or wishlist tools. There is no way to ask about them,
 * which is a stronger guarantee than a prompt instruction not to.
 *
 * <h2>Why no tool ever throws</h2>
 * Spring AI has no way to hand a model a failed tool call: an exception from a tool method
 * unwinds through the advisor chain and out of {@code POST /api/ai/chat} as a 500, killing a turn
 * that was already in progress. So every method here catches everything and returns an empty or
 * short value instead. "I found nothing" is a state the assistant can talk its way out of; "the
 * endpoint 500'd" is not.
 *
 * <h2>Why the fact strings are English</h2>
 * These strings are model input, not customer-facing copy — the assistant rewrites them into the
 * visitor's language. Keeping them in one language and one fixed shape is what lets a small model
 * parse them reliably; interleaving Arabic and English facts mid-conversation makes it far more
 * likely to paraphrase a number than to repeat it. The only strings a customer can see verbatim
 * are the degraded replies in {@code AiChatService}, and those are localized.
 *
 * <h2>Why the graph reads run through {@link TransactionOperations}</h2>
 * {@code getProductDetails} and {@link #cardsFor} walk {@code translations}, {@code variants},
 * {@code coffeeDetails}, {@code category} and {@code tastingNotes}, none of which
 * {@code findBySlug} join-fetches, so a persistence context has to be open while the facts are
 * assembled. It is opened <em>around the read only</em> — never around the model call, which can
 * take seconds and would pin a pooled connection for its whole duration.
 *
 * <p>A transaction callback rather than {@code @Transactional} because the tool methods are
 * invoked reflectively on the raw instance Spring AI was handed, not through a Spring proxy,
 * where an annotation on them would silently do nothing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiChatFunctions {

    /**
     * Ceiling on what one {@code search_products} call can return.
     *
     * <p>Three to five coffees is already more than a short reply can justify, and the model is
     * instructed to recommend two or three; the cap is what stops one enthusiastic query from
     * pulling a rail-sized result set out of the vector store.
     */
    public static final int MAX_RESULTS = 8;

    /**
     * How many coffees a tool call gets when the model does not say.
     *
     * <p>Two, not {@link #MAX_RESULTS}: a rail longer than a 120-word reply can justify is noise,
     * and this is the value used most often precisely because it is the one the model omits.
     */
    private static final int DEFAULT_RESULTS = 2;

    private static final int MIN_RESULTS = 1;

    /** Ceiling on the product rail attached to a reply. */
    private static final int MAX_CARDS = 8;

    private static final String UNKNOWN_PRODUCT =
            "No coffee in the catalog matches that. Report no match; do not suggest a coffee anyway.";

    private static final String INACTIVE_PRODUCT =
            "That coffee is not currently for sale. Report it as unavailable; do not describe it.";

    private static final String UNKNOWN_BREWING_METHOD =
            "No brewing guide is stored for that method. List the stored methods instead of "
                    + "describing one from memory.";

    private static final String FACT_SEPARATOR = " | ";

    private static final String LIST_SEPARATOR = ", ";

    private static final String ENGLISH = "en";

    /**
     * Descending similarity, id ascending as the tie-break.
     *
     * <p>Written out rather than composed so the null-safety is visible: entries are filtered
     * before sorting, and a score of {@code null} would otherwise become a {@code NullPointerException}
     * inside the comparator — i.e. inside the model call, which is the one place this must never
     * fail.
     */
    private static final Comparator<Map.Entry<Long, Double>> MOST_SIMILAR_FIRST = (left, right) -> {
        int byScore = Double.compare(right.getValue(), left.getValue());
        return byScore != 0 ? byScore : Long.compare(left.getKey(), right.getKey());
    };

    /**
     * Slugs returned by {@code search_products} while serving the turn this instance was minted
     * for.
     *
     * <p>Per-instance and never shared: the Spring-managed bean exists only to mint per-turn
     * instances through {@link #newTurn()}. A single shared list would let two concurrent visitors
     * recommending different coffees end up showing each other's product rail.
     */
    private final List<String> citedSlugs = new ArrayList<>();

    private final AiSearchService aiSearchService;
    private final ProductRepository productRepository;
    private final ProductResponseMapper productResponseMapper;
    private final BrewingMethodRepository brewingMethodRepository;
    private final TransactionOperations transactions;

    /**
     * Mints an instance for one conversation turn.
     *
     * <p>Called by {@code AiChatService} before it builds the {@code ChatClient}, so the tool
     * object handed to Spring AI — and therefore the slug collector the service reads afterwards —
     * belongs to exactly one request.
     */
    public AiChatFunctions newTurn() {
        return new AiChatFunctions(
                aiSearchService,
                productRepository,
                productResponseMapper,
                brewingMethodRepository,
                transactions);
    }

    /**
     * The slugs this turn's searches returned, in ranked order.
     *
     * @return an immutable snapshot; empty when the assistant searched for nothing
     */
    public List<String> citedSlugs() {
        return List.copyOf(citedSlugs);
    }

    // --- tools ------------------------------------------------------------------------------

    /**
     * Semantic search over the coffee catalog.
     *
     * <p>Returns slugs rather than display names on purpose: a slug is what the storefront can
     * resolve to a card and what the assistant must quote when it names a coffee, so a name the
     * model paraphrases cannot become an unresolvable recommendation.
     *
     * <p>Never throws. An empty list is returned when search is disabled, when nothing is indexed
     * yet, and when the embedding model is unreachable — all of which the assistant is told to
     * report as "no matches" rather than as a failure.
     *
     * <p>{@code limit} is a boxed {@link Integer} rather than an {@code int} on purpose. A tool
     * call is an untrusted JSON object produced by a small model, and {@code qwen2.5:3b-instruct}
     * routinely omits {@code limit} while still emitting {@code query}. Spring AI's
     * {@code MethodToolCallback} maps an absent key to {@code null} and reflectively invokes this
     * method, so a primitive parameter would make the missing argument a
     * {@code NullPointerException} on unboxing — and because that happens inside the tool loop it
     * discards the whole turn rather than the one argument. Treating the omission as "no stated
     * limit" costs a default; treating it as a failure costs the visitor their answer.
     *
     * @param query the visitor's own words, e.g. {@code "fruity light roast for pour over"}
     * @param limit how many coffees to consider, or {@code null} when the model omitted it;
     *              anything given is clamped to {@code [1, 8]}
     * @return up to {@code limit} slugs, most similar first; never null
     */
    @Tool(name = "search_products",
            description = """
                    Search the coffee catalog by taste, origin or brewing style and return product \
                    slugs, best match first. Call this whenever the visitor asks for a \
                    recommendation, names a taste they enjoy, or describes a drink they brew. \
                    Slugs are the only valid way to refer to a coffee — quote them verbatim.""")
    public List<String> searchProducts(
            @ToolParam(description = "The visitor's request in their own words, e.g. "
                    + "'fruity light roast for pour over'")
            String query,
            @ToolParam(description = "How many coffees to consider, 1 to 8")
            Integer limit) {

        try {
            if (!StringUtils.hasText(query)) {
                return List.of();
            }

            Map<Long, Double> candidates = aiSearchService.semanticCandidates(query, clampResults(limit));
            if (candidates.isEmpty()) {
                return List.of();
            }

            List<String> slugs = loadSlugs(candidates);
            slugs.forEach(citedSlugs::add);

            return List.copyOf(slugs);
        } catch (Exception e) {
            log.warn("Coffee catalog search unavailable, reporting no matches: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * The authoritative facts about one coffee.
     *
     * <p>Every value in the returned string — including every price — is read from the database.
     * Only variants that are active and therefore buyable are quoted, and a product with no active
     * variant reports its price as unknown rather than quoting a retired one.
     *
     * <p>Never throws; an unknown or unlisted slug answers with a short "report nothing"
     * instruction instead of a fact sheet.
     *
     * @param slug the slug {@code search_products} returned — never a name the visitor typed
     * @return a compact fact sheet, or a short instruction to report nothing
     */
    @Tool(name = "get_product_details",
            description = """
                    Read the catalog facts for one coffee: name, origin, farm, roast, process, \
                    variety, tasting notes, price range and availability. Call this before \
                    describing any specific coffee. Its answer is the only trustworthy source for \
                    that coffee's price and stock — repeat it, never restate it from memory.""")
    public String getProductDetails(
            @ToolParam(description = "The product slug exactly as returned by search_products")
            String slug) {

        try {
            if (!StringUtils.hasText(slug)) {
                return UNKNOWN_PRODUCT;
            }

            return inTransaction(() -> lookupForFacts(slug.trim()));
        } catch (Exception e) {
            log.warn("Coffee facts unavailable for slug {}, reporting nothing: {}", slug, e.getMessage());
            return UNKNOWN_PRODUCT;
        }
    }

    /**
     * The brewing guide stored for one method.
     *
     * <p>The description is the catalog's own translated text, returned as-is: a house method
     * description beats anything a small model could reconstruct, and passing it through verbatim
     * is the whole point.
     *
     * <p>Never throws; an unknown method answers with a short instruction to list what exists
     * rather than a description invented on the spot.
     *
     * @param method the brewing method slug, e.g. {@code v60}
     * @return the stored description, or a short instruction to report nothing
     */
    @Tool(name = "get_brewing_guide",
            description = """
                    Read the stored brewing guide for one method such as V60, French press, \
                    AeroPress, Chemex or espresso. Call this before answering any 'how do I brew \
                    it' question. Repeat the stored text; if the method is not stored, say so \
                    instead of describing it from memory.""")
    public String getBrewingGuide(
            @ToolParam(description = "The brewing method slug, e.g. 'v60', 'french-press', "
                    + "'aeropress', 'chemex', 'espresso'")
            String method) {

        try {
            if (!StringUtils.hasText(method)) {
                return UNKNOWN_BREWING_METHOD;
            }

            Optional<BrewingMethod> found = brewingMethodRepository
                    .findBySlugWithTranslations(method.trim().toLowerCase(Locale.ROOT));
            if (found.isEmpty()) {
                return UNKNOWN_BREWING_METHOD;
            }

            String description = descriptionFor(found.get(), currentLanguage());
            return StringUtils.hasText(description) ? description.trim() : UNKNOWN_BREWING_METHOD;
        } catch (Exception e) {
            log.warn("Brewing guide unavailable for method {}, reporting nothing: {}",
                    method, e.getMessage());
            return UNKNOWN_BREWING_METHOD;
        }
    }

    // --- product rail -----------------------------------------------------------------------

    /**
     * Resolves slugs into the cards the storefront renders beside the reply.
     *
     * <p>Order is the order the tools ranked them in, not the order the database returned them,
     * because that ranking is the model's evidence trail and re-sorting would throw it away. Slugs
     * the catalog no longer holds, and products no longer on sale, are dropped rather than
     * rendered as a blank card.
     *
     * <p>Never throws: a failed catalog read costs the rail, not the reply.
     *
     * @param slugs    slugs returned by {@link #searchProducts} during this turn
     * @param language the language the card names should be rendered in
     * @return at most {@link #MAX_CARDS} cards; empty when there is nothing to show, never null
     */
    public List<ProductCardResponse> cardsFor(List<String> slugs, String language) {
        try {
            if (slugs == null || slugs.isEmpty()) {
                return List.of();
            }

            List<String> wanted = slugs.stream()
                    .filter(StringUtils::hasText)
                    .map(slug -> slug.trim())
                    .distinct()
                    .limit(MAX_CARDS)
                    .toList();
            if (wanted.isEmpty()) {
                return List.of();
            }

            Map<String, Product> bySlug = new LinkedHashMap<>();
            inTransaction(() -> productRepository.findBySlugIn(wanted)).stream()
                    .filter(Product::isActive)
                    .filter(product -> StringUtils.hasText(product.getSlug()))
                    .forEach(product -> bySlug.putIfAbsent(product.getSlug(), product));

            List<ProductCardResponse> cards = new ArrayList<>();
            for (String slug : wanted) {
                Product product = bySlug.get(slug);
                if (product == null) {
                    continue;
                }
                // A card the mapper cannot project — a product whose category is missing from the
                // catalog — is skipped rather than appended as a null the client would render as a
                // broken tile.
                ProductCardResponse card = productResponseMapper.buildListResponse(product, language);
                if (card != null) {
                    cards.add(card);
                }
            }
            return cards;
        } catch (Exception e) {
            log.warn("Product cards unavailable, replying without them: {}", e.getMessage());
            return List.of();
        }
    }

    // --- catalog reads ----------------------------------------------------------------------

    /**
     * Fetches one product for the fact sheet, or the instruction to report nothing.
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

    /**
     * Turns the best-scoring ids into slugs, most similar first.
     *
     * <p>The score is used exactly as {@link AiSearchService} returned it — a similarity, higher
     * better. Applying {@code 1 - score} here would invert the rail and promote the least similar
     * coffee in the catalog.
     */
    private List<String> loadSlugs(Map<Long, Double> candidates) {
        List<Long> ranked = candidates.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .sorted(MOST_SIMILAR_FIRST)
                .map(Map.Entry::getKey)
                .limit(MAX_RESULTS)
                .toList();
        if (ranked.isEmpty()) {
            return List.of();
        }

        // Ranked before the read, then joined by id: the repository returns rows in whatever order
        // the database felt like, and preserving the similarity order is the entire point of the
        // ranking. Re-sorting the loaded products by score is impossible — the score is not on the
        // entity — so the order has to be reconstructed from the id ranking here.
        Map<Long, Product> byId = new LinkedHashMap<>();
        for (Product product : productRepository.findAllByIdInWithDetails(ranked)) {
            if (product != null && product.getId() != null) {
                byId.putIfAbsent(product.getId(), product);
            }
        }

        return ranked.stream()
                .map(byId::get)
                // An indexed row can outlive the product being on sale, and a card for a delisted
                // coffee is the one "recommendation" a shopper definitely cannot act on.
                .filter(product -> product != null && product.isActive())
                .filter(product -> StringUtils.hasText(product.getSlug()))
                .map(Product::getSlug)
                .distinct()
                .toList();
    }

    // --- fact assembly ----------------------------------------------------------------------

    private String describe(Product product) {
        String language = currentLanguage();

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
        String translated = nameIn(translations.apply(relation), language, nameOf);
        if (StringUtils.hasText(translated)) {
            target.add(label + ": " + translated);
        }
    }

    /**
     * The name in the request language, then in English.
     *
     * <p>The request language first because the facts are what the assistant paraphrases into the
     * visitor's language, and English second because it is the only translation guaranteed to
     * exist. No slug fallback: a slug inside a fact sheet reads as a fact.
     */
    private <T> String nameIn(List<T> translations, String language, Function<T, String> nameOf) {
        String match = pick(translations, language, nameOf);
        return StringUtils.hasText(match) ? match : pick(translations, ENGLISH, nameOf);
    }

    private <T> String pick(List<T> translations, String language, Function<T, String> nameOf) {
        if (translations == null) {
            return null;
        }
        return translations.stream()
                .filter(translation -> languageOf(translation).equals(language))
                .map(nameOf)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    /** The language column of any translatable row, without a shared interface to depend on. */
    private static String languageOf(Object translation) {
        return switch (translation) {
            case ProductTranslation product -> product.getLanguage();
            case OriginTranslation origin -> origin.getLanguage();
            case FarmTranslation farm -> farm.getLanguage();
            case RoastLevelTranslation roast -> roast.getLanguage();
            case ProcessingMethodTranslation process -> process.getLanguage();
            case CoffeeVarietyTranslation variety -> variety.getLanguage();
            case TastingNoteTranslation note -> note.getLanguage();
            case BrewingMethodTranslation brewing -> brewing.getLanguage();
            default -> "";
        };
    }

    private String name(Product product, String language) {
        String translated = product.getTranslations().stream()
                .filter(translation -> translation.getLanguage().equals(language))
                .map(ProductTranslation::getName)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElseGet(() -> product.getTranslations().stream()
                        .map(ProductTranslation::getName)
                        .filter(StringUtils::hasText)
                        .findFirst()
                        .orElse(null));

        return StringUtils.hasText(translated) ? translated : product.getSlug();
    }

    private String tastingNotes(Product product, String language) {
        return product.getTastingNotes().stream()
                .filter(note -> note.getTranslations() != null)
                .map(note -> nameIn(note.getTranslations(), language, TastingNoteTranslation::getName))
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.joining(LIST_SEPARATOR));
    }

    /**
     * The price range over variants that are actually buyable.
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

    private String descriptionFor(BrewingMethod method, String language) {
        List<BrewingMethodTranslation> translations = method.getTranslations();
        if (translations == null) {
            return null;
        }

        String described = pick(translations, language, BrewingMethodTranslation::getDescription);
        if (StringUtils.hasText(described)) {
            return described;
        }
        return translations.stream()
                .map(BrewingMethodTranslation::getDescription)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private static String currentLanguage() {
        return LocaleContextHolder.getLocale().getLanguage();
    }

    private static int clampResults(Integer requested) {
        if (requested == null) {
            return DEFAULT_RESULTS;
        }
        return Math.min(Math.max(requested, MIN_RESULTS), MAX_RESULTS);
    }

    /** Runs a catalog read with a persistence context open, and nothing else. */
    private <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }
}