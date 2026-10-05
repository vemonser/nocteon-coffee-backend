package com.nocteon.nocteon_api.ai.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.nocteon.nocteon_api.ai.dto.response.SimilarProductResponse;
import com.nocteon.nocteon_api.common.exception.notFound.ProductNotFoundException;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.mapper.ProductResponseMapper;
import com.nocteon.nocteon_api.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Answers "what else should I look at" for a product page.
 *
 * <h2>This endpoint must never hard-fail</h2>
 * A product detail page is a storefront page, and a 500 on it because Ollama restarted costs a
 * sale — "no recommendations" is an entirely acceptable answer in comparison. So all three AI
 * failure modes degrade to {@link AttributeSimilarityFallback} instead of propagating:
 * <ol>
 * <li>the feature flag is off, so the vector path is never entered at all;</li>
 * <li>the store throws — an unreachable Ollama surfaces as a connection error, a WebClient
 * exception, or a wrapped runtime exception depending on transport;</li>
 * <li>the store answers but nothing usable comes back: nothing indexed yet, every hit
 * unscored, or the source product as the only neighbour of its own text.</li>
 * </ol>
 * The catch around the vector path is deliberately broad, and the log is a {@code warn} with
 * the message only: a stack trace per request would flood the log precisely when the failure
 * is hardest to diagnose.
 *
 * <h2>The vector path</h2>
 * Spring AI 2.0.1's {@link VectorStore} has no by-vector {@code similaritySearch} overload —
 * only {@code similaritySearch(SearchRequest)} — and {@link SearchRequest} has no
 * {@code queryEmbedding} field, so {@code PgVectorStore} embeds the query string itself. The
 * source product's own document text is therefore passed as the query. Identical text embeds
 * identically, which is what makes its own row its own nearest neighbour and the lookup work
 * at all. {@code EmbeddingModel} is deliberately not injected: there is no overload that
 * accepts a {@code float[]}, so embedding here would produce a vector with nowhere to go.
 *
 * <p>{@code topK} asks for one hit more than the limit because the source is always among the
 * hits and is always discarded; without that headroom a rail of {@code n} would only ever
 * show {@code n - 1} products.
 *
 * <p>The similarity is used <strong>as returned</strong>. Spring AI 2.0.1's
 * {@code PgVectorStore.DocumentRowMapper} already sets {@code Document.score = 1 - distance},
 * so {@link Document#getScore()} is a similarity in {@code [0,1]} where higher is better.
 * Converting again would invert the rail and promote the <em>least</em> similar product.
 *
 * <h2>Why the method is transactional</h2>
 * {@link ProductDocumentFactory} and the attribute weights both walk {@code category},
 * {@code origin}, {@code coffeeDetails} and {@code tastingNotes}, none of which
 * {@code findAllByIdInWithDetails} join-fetches. The projection has to happen while the
 * persistence context is still open. Read-only is safe here — unlike
 * {@link ProductIndexingService}, nothing on this path writes to the vector store.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimilarProductService {

    /** Upper bound on the returned rail; past this the client is no longer rendering a rail. */
    private static final int MAX_LIMIT = 20;

    private static final int MIN_LIMIT = 1;

    /**
     * Extra hits requested beyond the limit, because the source's own document is the nearest
     * neighbour of its own text and is always returned first.
     */
    private static final int SELF_HIT_ALLOWANCE = 1;

    /** Metadata key holding the product id on an indexed document. */
    private static final String PRODUCT_ID_KEY = "productId";

    /**
     * Accept-everything threshold, for the same reason as {@link AiSearchService}: cosine
     * similarity is not calibrated across embedding models, so ranking decides and no absolute
     * cutoff can be justified.
     */
    private static final double ACCEPT_ALL_SIMILARITY = 0.0;

    private final VectorStore vectorStore;
    private final AiSearchService aiSearchService;
    private final ProductDocumentFactory documentFactory;
    private final ProductRepository productRepository;
    private final ProductResponseMapper productResponseMapper;
    private final AttributeSimilarityFallback fallback;

    /**
     * Finds the products most similar to {@code slug}.
     *
     * @param slug   the product being viewed; an unknown slug raises
     *               {@link ProductNotFoundException} rather than yielding an empty list, since
     *               "this product does not exist" and "this product has no neighbours" are
     *               different answers
     * @param limit  how many to return, clamped to {@code [1, 20]}
     * @return up to {@code limit} ranked entries, each with a non-blank reason; empty only when
     *         the catalog genuinely offers nothing
     */
    @Transactional(readOnly = true)
    public List<SimilarProductResponse> findSimilar(String slug, int limit) {
        int effectiveLimit = clampLimit(limit);

        Product source = productRepository.findBySlug(slug)
                .orElseThrow(ProductNotFoundException::new);

        String language = LocaleContextHolder.getLocale().getLanguage();

        List<Scored> ranked = aiSearchService.isSearchEnabled()
                ? vectorRank(source, effectiveLimit)
                : List.<Scored>of();

        if (ranked.isEmpty()) {
            ranked = attributeRank(source, effectiveLimit);
        }

        return ranked.stream()
                .limit(effectiveLimit)
                .map(scored -> toResponse(source, scored, language))
                .toList();
    }

    /**
     * Ranks by vector similarity, or returns nothing so the caller falls back.
     *
     * <p>Never throws: an empty return means "no semantic signal", which the caller treats
     * identically to the flag being off.
     */
    private List<Scored> vectorRank(Product source, int limit) {
        try {
            List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(documentFactory.buildText(source))
                    .topK(limit + SELF_HIT_ALLOWANCE)
                    .similarityThreshold(ACCEPT_ALL_SIMILARITY)
                    .build());

            return orderByScore(resolveHits(hits, source));
        } catch (Exception e) {
            log.warn("Vector similarity unavailable for product {}, falling back to attributes: {}",
                    source.getSlug(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Turns hits into loaded products, dropping the source and anything unscoreable.
     *
     * <p>The source is excluded by id here as well as by the extra {@code topK}: a rail that
     * leads with the product already on screen is worse than no rail at all.
     */
    private List<Scored> resolveHits(List<Document> hits, Product source) {
        Map<Long, Double> bestPerProduct = new LinkedHashMap<>();

        for (Document hit : hits) {
            toHit(hit, source).ifPresent(hitScore ->
                    bestPerProduct.merge(hitScore.productId(), hitScore.score(), Math::max));
        }

        if (bestPerProduct.isEmpty()) {
            return List.of();
        }

        List<Long> ids = List.copyOf(bestPerProduct.keySet());

        return productRepository.findAllByIdInWithDetails(ids).stream()
                // A public storefront rail must never offer something that is not on sale, and a
                // store that was not yet pruned can still hold an indexed, now-inactive row.
                .filter(Product::isActive)
                .filter(candidate -> bestPerProduct.containsKey(candidate.getId()))
                .map(candidate -> new Scored(candidate, bestPerProduct.get(candidate.getId())))
                .toList();
    }

    private Optional<HitScore> toHit(Document hit, Product source) {
        Object productId = hit.getMetadata().get(PRODUCT_ID_KEY);
        Double score = hit.getScore();

        if (!(productId instanceof Number number) || score == null) {
            return Optional.empty();
        }
        if (source.getId() != null && source.getId().equals(number.longValue())) {
            return Optional.empty();
        }
        return Optional.of(new HitScore(number.longValue(), score));
    }

    /**
     * Ranks by shared attributes over the active catalog — the vector path's safety net.
     *
     * <p>Nothing is re-fetched per candidate: the products were already loaded to be ranked, so
     * the scored pair carries the entity rather than only its id.
     */
    private List<Scored> attributeRank(Product source, int limit) {
        List<Product> candidates = productRepository.findAll().stream()
                .filter(Product::isActive)
                .toList();

        Map<Long, Product> byId = new HashMap<>();
        candidates.forEach(candidate -> byId.put(candidate.getId(), candidate));

        return orderByScore(fallback.rank(source, candidates).entrySet().stream()
                // A hit can name an id the active catalog no longer holds if the catalog shifted
                // between the ranking and this read; it is dropped rather than fetched separately.
                .filter(entry -> entry.getValue() != null && byId.containsKey(entry.getKey()))
                .map(entry -> new Scored(byId.get(entry.getKey()), entry.getValue()))
                .toList())
                .stream()
                .limit(limit)
                .toList();
    }

    /**
     * Descending score, id ascending as the tie-break.
     *
     * <p>Ties are common — the attribute path gives an identical total to any two products
     * sharing the same combination — so without a deterministic second key the same request
     * could return a different order each time.
     */
    private List<Scored> orderByScore(List<Scored> scored) {
        List<Scored> sorted = new ArrayList<>(scored);
        sorted.sort(Comparator
                .comparingDouble(Scored::score).reversed()
                .thenComparing(scoredProduct -> scoredProduct.product().getId()));
        return sorted;
    }

    private SimilarProductResponse toResponse(Product source, Scored scored, String language) {
        Product candidate = scored.product();

        return SimilarProductResponse.builder()
                .product(productResponseMapper.buildListResponse(candidate, language))
                .reason(reasonFor(source, candidate, scored.score(), language))
                .score(scored.score())
                .build();
    }

    /**
     * Guards the response contract: a blank reason renders as an empty chip that reads as a
     * bug, so it is replaced even though the fallback never returns one.
     *
     * <p>The substitute comes from {@link AttributeSimilarityFallback#neutralReason(String)}
     * rather than a string of this class's own, so a reason can never be English-only on the
     * Arabic storefront just because this guard fired.
     */
    private String reasonFor(Product source, Product candidate, double score, String language) {
        String reason = fallback.reason(source, candidate, score, language);
        return StringUtils.hasText(reason) ? reason : fallback.neutralReason(language);
    }

    private int clampLimit(int limit) {
        return Math.min(Math.max(limit, MIN_LIMIT), MAX_LIMIT);
    }

    /**
     * A ranked product: the loaded entity plus the score it earned.
     *
     * <p>Holding the entity is what keeps the service to one query per path, instead of
     * re-fetching every hit just to render it. Private rather than a DTO because it never
     * leaves the service and carries no response-facing semantics.
     */
    private record Scored(Product product, double score) {
    }

    /** A vector hit resolved to an id, before the products themselves are loaded. */
    private record HitScore(Long productId, double score) {
    }
}