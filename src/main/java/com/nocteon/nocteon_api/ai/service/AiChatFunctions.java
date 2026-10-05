package com.nocteon.nocteon_api.ai.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.nocteon.nocteon_api.product.dto.response.ProductCardResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The three tools the assistant is allowed to call, and the only way a catalog fact reaches it.
 *
 * <h2>Why this class is the hallucination guardrail</h2>
 * A language model will happily produce a plausible origin, a plausible tasting note and a
 * plausible price for a coffee that does not exist. No system prompt reliably prevents that;
 * what prevents it is that there is <em>nowhere else for facts to come from</em>. Every name,
 * number and availability flag the tools return is read from the database and handed to the model
 * as a finished fact, so a wrong price cannot be written into a reply — the model can only fail to
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
 * endpoint 500'd" is not. The collaborators below are allowed to throw — the translation from
 * failure to instruction happens here, because only this class knows what the instruction is.
 *
 * <h2>Why the fact strings are English</h2>
 * These strings are model input, not customer-facing copy — the assistant rewrites them into the
 * visitor's language. Keeping them in one language and one fixed shape is what lets a small model
 * parse them reliably; interleaving Arabic and English facts mid-conversation makes it far more
 * likely to paraphrase a number than to repeat it. The only strings a customer can see verbatim
 * are the degraded replies in {@code AiChatService}, and those are localized.
 *
 * <h2>Why the reads run through {@code TransactionOperations} rather than {@code @Transactional}</h2>
 * {@code getProductDetails} and {@link #cardsFor} walk lazy relations that their lookups do not
 * fetch, so a persistence context has to be open while the facts are assembled. It is opened
 * <em>around the read only</em> — never around the model call, which can take seconds and would
 * pin a pooled connection for its whole duration.
 *
 * <p>A transaction callback rather than {@code @Transactional} because the tool methods are
 * invoked reflectively on the raw instance Spring AI was handed, not through a Spring proxy,
 * where an annotation on them would silently do nothing.
 *
 * <h2>Why each turn gets its own instance</h2>
 * The cited-slug collector below is per-instance and never shared. Two concurrent visitors
 * recommending different coffees would otherwise end up showing each other's product rail.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiChatFunctions {

    /**
     * Ceiling on what one {@code search_products} call can return.
     *
     * <p>Delegated to {@link CatalogSearch#MAX_RESULTS}, which owns the ranking, so the ceiling
     * cannot drift between the tool's description and the query it actually runs.
     */
    public static final int MAX_RESULTS = CatalogSearch.MAX_RESULTS;

    /**
     * The answer when the catalog cannot be searched at all.
     *
     * <p>The most important string in this class. {@code search_products} used to answer an empty
     * list for both "no coffee matches" and "the search backend is down", which collapsed two
     * very different situations into one indistinguishable result. The model read that as "the
     * catalog has nothing", said so correctly — and then, having nothing to offer, filled the
     * gap from its own weights. Measured live against {@code qwen2.5:3b-instruct} with the
     * vector store down: 5 of 12 turns named coffees that do not exist, including one fabricated
     * product slug, and every one of them landed in the sentence after a correct refusal.
     *
     * <p>So the two states are now separate answers. A refusal to answer is something the model
     * can obey; "I could not find anything" is an invitation.
     *
     * <p>Same convention as {@link CoffeeFactSheet#UNKNOWN_PRODUCT}: say what happened, say what the
     * model must do about it, and say what it must not do.
     *
     * <p>Package-private rather than private so {@code AiChatFunctionsTest} can assert the
     * error state returns <em>this</em> value rather than merely something non-empty — the
     * wording is the contract, not an implementation detail.
     */
    static final String SEARCH_UNAVAILABLE =
            "The coffee catalog cannot be searched right now, so you do not know what it contains. "
                    + "Tell the visitor that search is unavailable and offer nothing else. "
                    + "Do not name a coffee, an origin or a tasting note. Do not suggest "
                    + "something from memory.";

    /**
     * Slugs returned by {@code search_products} while serving the turn this instance was minted
     * for.
     *
     * <p>Per-instance and never shared: the Spring-managed bean exists only to mint per-turn
     * instances through {@link #newTurn()}.
     */
    private final List<String> citedSlugs = new ArrayList<>();

    private final CatalogSearch catalogSearch;
    private final CoffeeFactSheet factSheet;
    private final BrewingGuideLookup brewingGuides;
    private final ProductCardBuilder cards;

    /**
     * Mints an instance for one conversation turn.
     *
     * <p>Called by {@code AiChatService} before it builds the {@code ChatClient}, so the tool
     * object handed to Spring AI — and therefore the slug collector the service reads afterwards —
     * belongs to exactly one request. The collaborators are stateless and are shared; only the
     * collector is per-turn.
     */
    public AiChatFunctions newTurn() {
        return new AiChatFunctions(catalogSearch, factSheet, brewingGuides, cards);
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
     * <h2>Three answers, not two</h2>
     * The distinction this method exists to make is between <em>nothing matches</em> and
     * <em>I could not look</em>. Both used to answer with an empty list, which told the model
     * the same thing in two situations that call for opposite behaviour: "the catalog has
     * nothing for you" invites it to fill the gap from memory, and it does — see
     * {@link #SEARCH_UNAVAILABLE}. So:
     *
     * <ul>
     * <li><strong>Matches</strong> — the slugs, best first.</li>
     * <li><strong>Nothing matches</strong> — an empty list. The search worked. The model may
     * report that honestly and stop.</li>
     * <li><strong>The search could not run</strong> — the flag is off, or the vector store or
     * the embedding model threw. A single-element list holding
     * {@link #SEARCH_UNAVAILABLE}, which is an instruction rather than a result: the model
     * knows it does not know, and must not compensate.</li>
     * </ul>
     *
     * <p>Only the third is a single-element list, and only the second is empty. That is the
     * whole contract.
     *
     * <p>A blank query is answered with an empty list because it is the model's own mistake,
     * not the backend's: there is nothing to look up and nothing to report.
     *
     * <p>Never throws. Spring AI has no way to hand a model a failed tool call, so an exception
     * out of here unwinds through the advisor chain and out of the endpoint as a 500.
     *
     * <p>{@code limit} is a boxed {@link Integer} rather than an {@code int} on purpose. A tool
     * call is an untrusted JSON object produced by a model, and {@code qwen2.5:3b-instruct}
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
     * @return the matched slugs; an empty list when the search worked and matched nothing; a
     *         single-element list holding {@link #SEARCH_UNAVAILABLE} when it could not run
     */
    @Tool(name = "search_products",
            description = """
                    Search the coffee catalog by taste, origin or brewing style and return product \
                    slugs, best match first. Call this whenever the visitor asks for a \
                    recommendation, names a taste they enjoy, or describes a drink they brew. \
                    Slugs are the only valid way to refer to a coffee — quote them verbatim. \
                    An empty result means nothing in the catalog matches. If the result is a \
                    sentence saying the catalog cannot be searched, the search itself is broken — \
                    say so and name no coffee at all.""")
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

            // Checked before the search rather than inferred from its result: a disabled flag
            // returns an empty map, and reporting that as "no matches" is the lie this
            // method's three-way contract exists to stop telling.
            if (!catalogSearch.isSearchEnabled()) {
                log.warn("Catalog search is disabled, telling the assistant it cannot search");
                return unavailable();
            }

            List<String> slugs = catalogSearch.slugsFor(query, CatalogSearch.clampResults(limit));
            if (slugs.isEmpty()) {
                return List.of();
            }

            // The store answered. If the slugs do not survive the catalog read, the honest
            // answer is still "nothing to show you" rather than "the search is broken" — a
            // pruned or delisted row is a normal outcome, not an outage.
            citedSlugs.addAll(slugs);
            return List.copyOf(slugs);
        } catch (Exception e) {
            log.warn("Coffee catalog search unavailable: {}", e.getMessage());
            return unavailable();
        }
    }

    /**
     * The third answer: a one-element list, so it is structurally impossible to mistake for
     * either of the other two states.
     */
    private static List<String> unavailable() {
        return List.of(SEARCH_UNAVAILABLE);
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
                return CoffeeFactSheet.UNKNOWN_PRODUCT;
            }

            return factSheet.factsFor(slug.trim());
        } catch (Exception e) {
            log.warn("Coffee facts unavailable for slug {}, reporting nothing: {}", slug, e.getMessage());
            return CoffeeFactSheet.UNKNOWN_PRODUCT;
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
                return BrewingGuideLookup.UNKNOWN_BREWING_METHOD;
            }

            return brewingGuides.guideFor(BrewingGuideLookup.normalise(method));
        } catch (Exception e) {
            log.warn("Brewing guide unavailable for method {}, reporting nothing: {}",
                    method, e.getMessage());
            return BrewingGuideLookup.UNKNOWN_BREWING_METHOD;
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
     * @return the cards for those slugs; empty when there is nothing to show, never null
     */
    public List<ProductCardResponse> cardsFor(List<String> slugs, String language) {
        return cards.cardsFor(slugs, language);
    }
}