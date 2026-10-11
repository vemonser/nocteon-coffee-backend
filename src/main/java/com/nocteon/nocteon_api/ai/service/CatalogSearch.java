package com.nocteon.nocteon_api.ai.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

/**
 * Semantic search over the coffee catalog: the ranking, and the resolution of that ranking into
 * slugs.
 *
 * <h2>Why slugs and not display names</h2>
 * A slug is what the storefront can resolve to a card and what the assistant must quote when it
 * names a coffee. A name the model paraphrases cannot become an unresolvable recommendation, so
 * the model is never handed one to paraphrase in the first place.
 *
 * <h2>Why the score is consumed exactly as returned</h2>
 * Spring AI already stores {@code 1 - distance}, so a higher score is a <em>more</em> similar
 * coffee. Applying {@code 1 - score} again would invert the rail and promote the least similar
 * coffee in the catalog — a bug no test of "the results look reasonable" would catch, because the
 * returned set is identical either way.
 *
 * <h2>Why the id ranking is rebuilt after the read</h2>
 * Ranked before the read, then joined by id: the repository returns rows in whatever order the
 * database felt like, and preserving the similarity order is the entire point of the ranking.
 * Re-sorting the loaded products is impossible — the score is not on the entity — so the order has
 * to be reconstructed from the id ranking here.
 */
@Component
@RequiredArgsConstructor
public class CatalogSearch {

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

    private final AiSearchService aiSearchService;
    private final ProductRepository productRepository;

    /**
     * The slugs this query matched, most similar first.
     *
     * <p>Never throws. Spring AI has no way to hand a model a failed tool call, so an exception
     * out of here unwinds through the advisor chain and out of the endpoint as a 500.
     *
     * @param query  the visitor's own words
     * @param limit  how many coffees to consider, already clamped
     * @return the matched slugs; an empty list when the search ran and matched nothing, or when a
     *         candidate did not survive the catalog read
     */
    public List<String> slugsFor(String query, int limit) {
        Map<Long, Double> candidates = aiSearchService.semanticCandidates(query, limit);
        return loadSlugs(candidates);
    }

    /**
     * Turns the best-scoring ids into slugs, most similar first.
     *
     * <p>A candidate the catalog no longer holds is dropped rather than reported as a match: an
     * indexed row can outlive the product being on sale, and a card for a delisted coffee is the
     * one "recommendation" a shopper definitely cannot act on.
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

        Map<Long, Product> byId = new LinkedHashMap<>();
        for (Product product : productRepository.findAllByIdInWithDetails(ranked)) {
            if (product != null && product.getId() != null) {
                byId.putIfAbsent(product.getId(), product);
            }
        }

        return ranked.stream()
                .map(byId::get)
                .filter(product -> product != null && product.isActive())
                .filter(product -> StringUtils.hasText(product.getSlug()))
                .map(Product::getSlug)
                .distinct()
                .toList();
    }

    /**
     * Whether the catalog can be searched at all.
     *
     * <p>Checked before the search rather than inferred from its result: a disabled flag returns an
     * empty map, and reporting that as "no matches" is the lie the tool's three-way contract
     * exists to stop telling.
     */
    public boolean isSearchEnabled() {
        return aiSearchService.isSearchEnabled();
    }

    /**
     * Clamps a model-supplied limit into {@code [1, 8]}.
     *
     * <p>A tool call is an untrusted JSON object produced by a model, and {@code qwen2.5:3b-instruct}
     * routinely omits {@code limit} while still emitting {@code query}. Spring AI's
     * {@code MethodToolCallback} maps an absent key to {@code null} and reflectively invokes the
     * tool method, so a primitive parameter would make the missing argument a
     * {@code NullPointerException} on unboxing — and because that happens inside the tool loop it
     * discards the whole turn rather than the one argument. Treating the omission as "no stated
     * limit" costs a default; treating it as a failure costs the visitor their answer.
     */
    public static int clampResults(Integer requested) {
        if (requested == null) {
            return DEFAULT_RESULTS;
        }
        return Math.min(Math.max(requested, MIN_RESULTS), MAX_RESULTS);
    }

    }