package com.nocteon.nocteon_api.product.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.ai.service.AiSearchService;
import com.nocteon.nocteon_api.ai.service.HybridRanker;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.exception.notFound.ProductNotFoundException;
import com.nocteon.nocteon_api.common.util.ProductSortResolver;
import com.nocteon.nocteon_api.product.dto.request.ProductFilterRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductMediaRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductRequest;
import com.nocteon.nocteon_api.product.dto.response.DashboardProductResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductCardResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductOptionOptionResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductOptionValueOptionResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductResponse;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductOption;
import com.nocteon.nocteon_api.product.entity.ProductOptionTranslation;
import com.nocteon.nocteon_api.product.entity.ProductOptionValue;
import com.nocteon.nocteon_api.product.entity.ProductOptionValueTranslation;
import com.nocteon.nocteon_api.product.mapper.DashboardProductMapper;
import com.nocteon.nocteon_api.product.mapper.ProductResponseMapper;
import com.nocteon.nocteon_api.product.repository.ProductOptionValueRepository;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;
import com.nocteon.nocteon_api.product.repository.specification.ProductSpecifications;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

    /**
     * How many products to ask the vector store for on a semantic search.
     *
     * <p>Deliberately larger than a page so ranking has something to reorder, and small enough
     * that the one facet-constrained query below stays cheap.
     */
    private static final int SEMANTIC_CANDIDATE_LIMIT = 50;

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductResponseMapper productResponseMapper;
    private final DashboardProductMapper dashboardProductMapper;
    private final ProductMutationService productMutationService;
    private final ProductOptionValueRepository productOptionValueRepository;
    private final AiSearchService aiSearchService;
    private final HybridRanker hybridRanker;

    /**
     * The public catalog listing. Answers with vector-similarity ranking when the caller opts
     * in via {@code ?semantic=true} with real search text, and with the existing keyword query
     * otherwise — including whenever the semantic path cannot answer.
     *
     * @return the requested page of cards; the response shape is identical either way, only
     *         the ordering and the page membership differ
     */
    public PageResponse<ProductCardResponse> getAll(ProductFilterRequest filter) {
        if (Boolean.TRUE.equals(filter.getSemantic())
                && StringUtils.hasText(filter.getSearch())
                && aiSearchService.isSearchEnabled()) {
            return semanticSearch(filter);
        }

        return keywordSearch(filter);
    }

    private PageResponse<ProductCardResponse> keywordSearch(ProductFilterRequest filter) {
        String language = LocaleContextHolder.getLocale().getLanguage();

        String sort = switch (filter.getSort()) {
            case "name", "price", "createdAt", "featured" -> filter.getSort();
            default -> "createdAt";
        };

        filter.setSort(sort);

        Specification<Product> spec = ProductSpecifications.withFilters(filter, language,
                countDistinctOptions(filter));
        Sort resolvedSort = ProductSortResolver.resolve(sort, filter.getDirection());
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), resolvedSort);

        Page<Product> page = productRepository.findAll(spec, pageable);

        return PageResponse.of(
                page.map(p -> productResponseMapper.buildListResponse(p, language)));
    }

    /**
     * Ranks the catalog by vector similarity instead of by name matching.
     *
     * <p>Three steps, in this order: ask the vector store for candidates, re-apply the user's
     * facets to them in SQL, then rank what survived in memory. Sorting by a blended score cannot
     * be expressed in JPA, so the candidate query is deliberately unsorted and the page is cut
     * from the ranked list here.
     *
     * <p>Degrades to {@link #keywordSearch(ProductFilterRequest)} whenever the semantic signal
     * is unusable — the store is unreachable, nothing is indexed yet, or every candidate was
     * excluded by a facet filter — because an empty page is a worse answer than the plain
     * listing the client would have got anyway. {@link AiSearchService#semanticCandidates} never
     * throws, so there is nothing to catch here.
     */
    private PageResponse<ProductCardResponse> semanticSearch(ProductFilterRequest filter) {
        String language = LocaleContextHolder.getLocale().getLanguage();

        Map<Long, Double> vectorScores = aiSearchService.semanticCandidates(
                filter.getSearch(), SEMANTIC_CANDIDATE_LIMIT);

        if (vectorScores.isEmpty()) {
            return keywordSearch(filter);
        }

        List<Product> candidates = findSemanticCandidates(filter, language, vectorScores.keySet());

        if (candidates.isEmpty()) {
            return keywordSearch(filter);
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
     * <p>Separate from {@link #copyWithoutOptionValueIds(ProductFilterRequest)}, which does the
     * opposite for the option facets: these are what constrain a search, those are what get
     * aggregated separately.
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

    // في ProductService
    public List<ProductOptionOptionResponse> getAvailableOptions(ProductFilterRequest filter) {
        String language = LocaleContextHolder.getLocale().getLanguage();

        ProductFilterRequest filterWithoutOptions = copyWithoutOptionValueIds(filter);

        Specification<Product> spec = ProductSpecifications.withFilters(filterWithoutOptions, language, 0);
        List<Long> productIds = productRepository.findAll(spec).stream()
                .map(Product::getId)
                .distinct()
                .toList();

        if (productIds.isEmpty()) {
            return List.of();
        }

        List<ProductOptionValue> optionValues = productVariantRepository
                .findDistinctOptionValuesByProductIds(productIds);

        optionValues.sort(
                Comparator.comparing((ProductOptionValue ov) -> ov.getOption().getSortOrder())
                        .thenComparing(ProductOptionValue::getSortOrder));

        Map<Long, String> optionNames = new LinkedHashMap<>();
        Map<Long, List<ProductOptionValueOptionResponse>> valuesByOption = new LinkedHashMap<>();

        for (ProductOptionValue ov : optionValues) {
            ProductOption option = ov.getOption();

            optionNames.computeIfAbsent(option.getId(), id -> option.getTranslations().stream()
                    .filter(t -> t.getLanguage().equals(language))
                    .findFirst()
                    .map(ProductOptionTranslation::getName)
                    .orElse(option.getSlug()));

            String value = ov.getTranslations().stream()
                    .filter(t -> t.getLanguage().equals(language))
                    .findFirst()
                    .map(ProductOptionValueTranslation::getValue)
                    .orElse(ov.getSlug());

            valuesByOption
                    .computeIfAbsent(option.getId(), id -> new ArrayList<>())
                    .add(ProductOptionValueOptionResponse.builder()
                            .id(ov.getId())
                            .slug(ov.getSlug())
                            .value(value)
                            .build());
        }

        return optionNames.entrySet().stream()
                .map(entry -> {
                    Long optionId = entry.getKey();
                    // نجيب slug الأوبشن من أول value بتاعتها (كلهم بنفس option فمتساوين)
                    String optionSlug = optionValues.stream()
                            .filter(ov -> ov.getOption().getId().equals(optionId))
                            .findFirst()
                            .map(ov -> ov.getOption().getSlug())
                            .orElse("");

                    return ProductOptionOptionResponse.builder()
                            .id(optionId)
                            .slug(optionSlug)
                            .name(entry.getValue())
                            .values(valuesByOption.get(optionId))
                            .build();
                })
                .toList();
    }

    private ProductFilterRequest copyWithoutOptionValueIds(ProductFilterRequest filter) {
        ProductFilterRequest copy = new ProductFilterRequest();
        copy.setSearch(filter.getSearch());
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
        copy.setInStockOnly(filter.getInStockOnly());
        copy.setOnSaleOnly(filter.getOnSaleOnly());
        return copy;
    }

    public PageResponse<ProductCardResponse> getAllDashboard(ProductFilterRequest filter) {
        String language = LocaleContextHolder.getLocale().getLanguage();

        Page<Product> page = findDashboardPage(filter);

        return PageResponse.of(
                page.map(p -> productResponseMapper.buildListResponse(p, language)));
    }

    public DashboardProductResponse getDashboardBySlug(String slug) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(ProductNotFoundException::new);

        return dashboardProductMapper.buildDashboardResponse(product);
    }

    public ProductResponse getBySlug(String slug) {
        String language = LocaleContextHolder.getLocale().getLanguage();

        Product product = productRepository.findBySlugAndLanguage(slug, language)
                .orElseThrow(ProductNotFoundException::new);

        return productResponseMapper.buildDetailResponse(product, language);
    }

    public ProductResponse create(ProductRequest request, List<MultipartFile> files) {
        return productMutationService.create(request, files);
    }

    public DashboardProductResponse update(String slug, ProductRequest request, List<MultipartFile> files) {
        return productMutationService.update(slug, request, files);
    }

    public DashboardProductResponse toggleActive(String slug, boolean active) {
        return productMutationService.toggleActive(slug, active);
    }

    public DashboardProductResponse toggleFeatured(String slug, boolean featured) {
        return productMutationService.toggleFeatured(slug, featured);
    }

    public ProductResponse uploadMedia(
            String slug,
            List<ProductMediaRequest> mediaRequests,
            List<MultipartFile> files) {

        return productMutationService.uploadMedia(slug, mediaRequests, files);
    }

    public void deleteMedia(Long mediaId) {
        productMutationService.deleteMedia(mediaId);
    }

    public void delete(String slug) {
        productMutationService.delete(slug);
    }

    private Page<Product> findDashboardPage(ProductFilterRequest filter) {
        return productRepository.findAllDashboard(
                Objects.requireNonNullElse(filter.getSearch(), ""),
                filter.getCategorySlug(),
                filter.getProductType(),
                filter.getIsActive(),
                filter.getFeatured(),
                filter.toPageable());
    }
}
