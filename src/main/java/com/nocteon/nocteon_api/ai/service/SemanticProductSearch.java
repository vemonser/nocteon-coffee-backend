package com.nocteon.nocteon_api.ai.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.product.dto.request.ProductFilterRequest;
import com.nocteon.nocteon_api.product.dto.response.ProductCardResponse;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.mapper.ProductResponseMapper;
import com.nocteon.nocteon_api.product.repository.ProductOptionValueRepository;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.product.repository.specification.ProductSpecifications;

import lombok.RequiredArgsConstructor;

/**
 * Ranks the catalog by vector similarity instead of by name matching.
 *
 * <h2>Why the page is cut in memory</h2>
 * Three steps, in this order: ask the vector store for candidates, re-apply the user's facets to
 * them in SQL, then rank what survived in memory. Sorting by a blended score cannot be expressed
 * in JPA, so the candidate query is deliberately unsorted and the page is cut here.
 *
 * <h2>Why the facets are re-applied rather than trusted</h2>
 * A vector store knows nothing about this session's filters. If the candidate set were taken as
 * ranked, a shopper filtering to "decaf, under 100, in stock" would get in-stock full-caffeine
 * coffee at a price they excluded. Semantic ranking changes the <em>order</em>; it must never
 * change the <em>set</em>.
 *
 * <h2>Why this degrades rather than returning an empty page</h2>
 * Whenever the semantic signal is unusable — the store is unreachable, nothing is indexed yet, or
 * every candidate was excluded by a facet filter — the caller's keyword path answers instead, via
 * {@link Optional#empty()} from {@link #rank}. An empty page would read to the client as "we have
 * nothing matching that", which is a different and wrong claim; the plain listing is what the
 * client would have got anyway. {@link AiSearchService#semanticCandidates} never throws, so there
 * is nothing to catch here.
 *
 * <h2>Why the score is never converted</h2>
 * {@code getScore()} is already a similarity — higher is better. Applying {@code 1 - score} would
 * invert the page and promote the least similar coffee in the catalog.
 */
@Service
@RequiredArgsConstructor
public class SemanticProductSearch {

    /**
     * How many products to ask the vector store for on a semantic search.
     *
     * <p>Deliberately larger than a page so ranking has something to reorder, and small enough
     * that the one facet-constrained query below stays cheap.
     */
    static final int SEMANTIC_CANDIDATE_LIMIT = 50;

    private final AiSearchService aiSearchService;
    private final HybridRanker hybridRanker;
    private final ProductRepository productRepository;
    private final ProductOptionValueRepository productOptionValueRepository;
    private final ProductResponseMapper productResponseMapper;

    /**
     * Whether this request asked for semantic search and the backend can answer it.
     *
     * <p>Three conditions, all of which must hold, and the order matters: the flag and the search
     * text are checked before the backend is asked anything at all, so an ordinary catalog browse
     * with {@code semantic=true} but no search text never touches {@link AiSearchService} or
     * {@link HybridRanker}.
     *
     * @return {@link Optional#empty()} when the request does not qualify, or when the caller
     *         should fall back to the keyword path
     */
    public Optional<PageResponse<ProductCardResponse>> rank(ProductFilterRequest filter) {
        if (!Boolean.TRUE.equals(filter.getSemantic())
                || !StringUtils.hasText(filter.getSearch())
                || !aiSearchService.isSearchEnabled()) {
            return Optional.empty();
        }

        String language = LocaleContextHolder.getLocale().getLanguage();

        Map<Long, Double> vectorScores = aiSearchService.semanticCandidates(
                filter.getSearch(), SEMANTIC_CANDIDATE_LIMIT);

        if (vectorScores.isEmpty()) {
            return Optional.empty();
        }

        List<Product> candidates = findSemanticCandidates(filter, language, vectorScores.keySet());

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        // The keyword rank list is empty on this path: the candidate set was already narrowed to
        // the vector hits and re-filtered in SQL, so a rank within it would measure nothing a
        // customer can observe. HybridRanker still owns the weighting, so the vector signal keeps
        // the same scale it has anywhere else.
        Map<Long, Double> blendedScores = hybridRanker.blend(List.of(), vectorScores);

        List<Product> ranked = candidates.stream()
                .filter(p -> blendedScores.containsKey(p.getId()))
                .sorted(Comparator
                        .comparingDouble((Product p) -> blendedScores.get(p.getId())).reversed()
                        // Equal scores are common once several products share a vector chunk, so
                        // the id breaks the tie and keeps paging stable across requests.
                        .thenComparing(Product::getId))
                .toList();

        return Optional.of(page(ranked, filter, language));
    }

    private PageResponse<ProductCardResponse> page(
            List<Product> ranked, ProductFilterRequest filter, String language) {

        List<Product> pageContent = slice(ranked, filter.getPage(), filter.getSize());

        return PageResponse.<ProductCardResponse>builder()
                .content(pageContent.stream()
                        .map(p -> productResponseMapper.buildListResponse(p, language))
                        .toList())
                .page(filter.getPage())
                .size(filter.getSize())
                // The filtered candidate count, not the vector top-K: a page count the client
                // cannot walk to is a contract break, and facet-excluded candidates were never
                // on offer.
                .totalElements(ranked.size())
                .totalPages(totalPages(ranked.size(), filter.getSize()))
                .first(filter.getPage() == 0)
                .last(filter.getPage() + 1 >= totalPages(ranked.size(), filter.getSize()))
                .build();
    }

    /**
     * Loads the vector candidates that the user's own filters still allow.
     *
     * <p>Runs {@link ProductSpecifications#withFilters} unchanged and only adds
     * {@code id in (candidates)} on top, so category, price, rating, stock, sale and
     * option-value predicates keep constraining semantic results — semantic ranking changes the
     * order, never the set the customer is allowed to see.
     *
     * <p>The search text is dropped from that filter on purpose. Its predicate is a
     * {@code LIKE} against the product <em>name</em>, and re-applying it would throw away
     * precisely the products semantic search exists to surface: a product described as "berry
     * notes" must be findable by the word "fruity". Facet filters are the ones that describe
     * what the customer is willing to buy, and those are all kept.
     */
    private List<Product> findSemanticCandidates(
            ProductFilterRequest filter,
            String language,
            Set<Long> candidateIds) {

        ProductFilterRequest facetFilter = copyWithoutSearch(filter);

        Specification<Product> facets = ProductSpecifications.withFilters(
                facetFilter, language, countDistinctOptions(facetFilter));

        Specification<Product> spec = facets.and(
                (root, query, cb) -> root.get("id").in(candidateIds));

        return productRepository.findAll(spec, Sort.unsorted());
    }

    /**
     * Copies every filter except the free-text search term.
     *
     * <p>Separate from the keyword path's copy, which does the opposite for the option facets:
     * these are what constrain a search, those are what get aggregated separately.
     */
    private ProductFilterRequest copyWithoutSearch(ProductFilterRequest filter) {
        ProductFilterRequest copy = new ProductFilterRequest();
        copy.setCategorySlug(filter.getCategorySlug());
        copy.setOriginSlug(filter.getOriginSlug());
        copy.setFarmSlug(filter.getFarmSlug());
        copy.setMinRating(filter.getMinRating());
        copy.setProductType(filter.getProductType());
        copy.setFeatured(filter.getFeatured());
        copy.setIsActive(filter.getIsActive());
        copy.setMinPrice(filter.getMinPrice());
        copy.setMaxPrice(filter.getMaxPrice());
        copy.setBrewingMethodSlug(filter.getBrewingMethodSlug());
        copy.setOptionValueIds(filter.getOptionValueIds());
        copy.setInStockOnly(filter.getInStockOnly());
        copy.setOnSaleOnly(filter.getOnSaleOnly());
        copy.setSort(filter.getSort());
        copy.setDirection(filter.getDirection());
        return copy;
    }

    /**
     * How many distinct options the requested option values span, which
     * {@link ProductSpecifications} needs to require <em>all</em> of them. Zero when no option
     * filter was asked for.
     */
    private long countDistinctOptions(ProductFilterRequest filter) {
        if (filter.getOptionValueIds() == null || filter.getOptionValueIds().isEmpty()) {
            return 0;
        }
        return productOptionValueRepository
                .countDistinctOptionsByValueIds(filter.getOptionValueIds());
    }

    /** The requested window of an already-ranked list, clamped to what it actually holds. */
    private static <T> List<T> slice(List<T> ranked, int page, int size) {
        if (size <= 0) {
            return List.of();
        }
        long from = Math.min((long) page * size, ranked.size());
        int to = (int) Math.min(from + size, ranked.size());
        return ranked.subList((int) from, to);
    }

    private static int totalPages(long totalElements, int size) {
        return size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
}