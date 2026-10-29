package com.nocteon.nocteon_api.product.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.nocteon.nocteon_api.ai.service.AiSearchService;
import com.nocteon.nocteon_api.ai.service.HybridRanker;
import com.nocteon.nocteon_api.ai.service.SemanticProductSearch;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.product.dto.request.ProductFilterRequest;
import com.nocteon.nocteon_api.product.dto.response.ProductCardResponse;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.mapper.DashboardProductMapper;
import com.nocteon.nocteon_api.product.mapper.ProductResponseMapper;
import com.nocteon.nocteon_api.product.repository.ProductOptionValueRepository;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;
import com.nocteon.nocteon_api.product.repository.specification.ProductSpecifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the {@code ?semantic=true} branch of
 * {@link ProductService#getAll(ProductFilterRequest)}.
 *
 * <p>Two things are pinned here. First, that the vector store and the ranker are <em>never</em>
 * touched unless the request actually asks for semantic search with a non-blank query — the
 * existing SQL path must stay exactly what it was when the feature is off. Second, that the
 * user's facet filters still constrain the semantic candidate set, because ranking by similarity
 * must never widen what the user asked for.
 *
 * <p>Everything is mocked at the repository boundary. The candidate fetch is asserted against a
 * {@link Specification} rather than a real query, which is what makes "the facets are applied in
 * SQL, not re-implemented in Java" observable without a database.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceSemanticTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private ProductResponseMapper productResponseMapper;

    @Mock
    private DashboardProductMapper dashboardProductMapper;

    @Mock
    private ProductMutationService productMutationService;

    @Mock
    private ProductOptionValueRepository productOptionValueRepository;

    @Mock
    private AiSearchService aiSearchService;

    @Mock
    private HybridRanker hybridRanker;

    /** FE-14: the dashboard card's rating aggregate; untouched on the semantic path. */
    @Mock
    private com.nocteon.nocteon_api.review.service.ReviewService reviewService;

    private ProductService service;

    /**
     * Built explicitly rather than through {@code @InjectMocks}: the semantic path moved to
     * {@code SemanticProductSearch}, and the point of these tests is that the AI collaborators are
     * reachable through the real production wiring. A mock for the new collaborator would make
     * every {@code verifyNoInteractions} below pass vacuously.
     */
    @BeforeEach
    void setUp() {
        service = new ProductService(
                productRepository,
                productVariantRepository,
                productResponseMapper,
                dashboardProductMapper,
                productMutationService,
                productOptionValueRepository,
                new SemanticProductSearch(
                        aiSearchService,
                        hybridRanker,
                        productRepository,
                        productOptionValueRepository,
                        productResponseMapper,
                        productVariantRepository),
                reviewService);
    }

    /**
     * Cards carry the product id so ordering can be asserted on the response itself. Lenient
     * because the tests that never reach the mapper would otherwise fail on an unused stub.
     */
    private void stubCardMapping() {
        lenient().when(productResponseMapper.buildListResponse(any(Product.class), anyString(), any()))
                .thenAnswer(invocation -> ProductCardResponse.builder()
                        .id(((Product) invocation.getArgument(0)).getId())
                        .build());
    }

    private static Product product(long id) {
        return Product.builder().id(id).build();
    }

    private static ProductFilterRequest semanticFilter(String search) {
        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setSearch(search);
        filter.setSemantic(true);
        filter.setPage(0);
        filter.setSize(10);
        return filter;
    }

    /** A specification with no predicates, used where only the filter argument matters. */
    private static Specification<Product> noPredicates() {
        return (root, query, cb) -> cb.conjunction();
    }

    // --- the short circuit ---------------------------------------------------------------

    /**
     * Review Focus item 1: {@code semantic=true} with no search text is an ordinary catalog
     * browse, so the AI collaborators must not even be asked whether the feature is on.
     */
    @Test
    void semanticFlagIsIgnoredWhenSearchIsBlank() {
        ProductFilterRequest filter = semanticFilter("   ");
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        PageResponse<ProductCardResponse> page = service.getAll(filter);

        assertThat(page.getTotalElements()).isZero();
        verifyNoInteractions(aiSearchService, hybridRanker);
    }

    @Test
    void semanticFlagIsIgnoredWhenSearchIsAbsent() {
        ProductFilterRequest filter = semanticFilter(null);
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getAll(filter);

        verifyNoInteractions(aiSearchService, hybridRanker);
    }

    @Test
    void semanticFlagIsIgnoredWhenTheFeatureIsOff() {
        ProductFilterRequest filter = semanticFilter("fruity");
        when(aiSearchService.isSearchEnabled()).thenReturn(false);
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        PageResponse<ProductCardResponse> page = service.getAll(filter);

        assertThat(page.getTotalElements()).isZero();
        verify(aiSearchService, never()).semanticCandidates(anyString(), anyInt());
        verifyNoInteractions(hybridRanker);
    }

    /**
     * The default request — no {@code semantic} flag at all — stays on the untouched keyword
     * path: a paged query in, a paged query out, and no vector work.
     */
    @Test
    void aPlainRequestNeverTouchesTheAiCollaborators() {
        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setSearch("yirgacheffe");
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getAll(filter);

        verify(productRepository).findAll(any(Specification.class), any(Pageable.class));
        verifyNoInteractions(aiSearchService, hybridRanker);
    }

    @Test
    void semanticFalseIsNotTreatedAsSemantic() {
        ProductFilterRequest filter = semanticFilter("fruity");
        filter.setSemantic(false);
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getAll(filter);

        verifyNoInteractions(aiSearchService, hybridRanker);
    }

    /**
     * The seam the extraction introduced: an ordinary browse must reach the keyword query with
     * {@code SemanticProductSearch} still completely untouched. Before the split the flag check
     * lived in this class; it now lives behind the collaborator, so this is the assertion that
     * keeps the collaborator itself from becoming the thing that is always consulted.
     */
    @Test
    void aRequestThatCannotBeAnsweredSemanticallyRunsExactlyOneQuery() {
        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setSearch("yirgacheffe");
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getAll(filter);

        verify(productRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
        verifyNoInteractions(aiSearchService, hybridRanker);
    }

    // --- ranking -------------------------------------------------------------------------

    @Test
    void semanticRankingOrdersThePageByBlendedScore() {
        ProductFilterRequest filter = semanticFilter("fruity light roast");
        stubCardMapping();
        when(aiSearchService.isSearchEnabled()).thenReturn(true);
        when(aiSearchService.semanticCandidates("fruity light roast", 50))
                .thenReturn(Map.of(1L, 0.40, 2L, 0.92));
        when(hybridRanker.blend(anyList(), anyMap()))
                .thenReturn(Map.of(1L, 0.20, 2L, 0.61));
        // The repository order is deliberately the opposite of the ranking.
        when(productRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(product(1L), product(2L)));

        PageResponse<ProductCardResponse> page = service.getAll(filter);

        assertThat(page.getContent()).extracting(ProductCardResponse::getId)
                .containsExactly(2L, 1L);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    /**
     * The similarities reach the ranker untouched: Spring AI 2.0.1 already stores
     * {@code 1 - distance}, so re-deriving {@code 1 - score} here would invert the order.
     */
    @Test
    void vectorScoresAreHandedToTheRankerWithoutConversion() {
        ProductFilterRequest filter = semanticFilter("fruity");
        stubCardMapping();
        when(aiSearchService.isSearchEnabled()).thenReturn(true);
        when(aiSearchService.semanticCandidates("fruity", 50)).thenReturn(Map.of(7L, 0.83));
        when(hybridRanker.blend(anyList(), anyMap())).thenReturn(Map.of(7L, 0.50));
        when(productRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(product(7L)));

        service.getAll(filter);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Long, Double>> scores = ArgumentCaptor.forClass(Map.class);
        verify(hybridRanker).blend(anyList(), scores.capture());
        assertThat(scores.getValue()).containsExactly(Map.entry(7L, 0.83));
    }

    /**
     * Candidates arrive from a {@link Specification} rather than an id lookup, which is what
     * keeps the facet predicates in SQL instead of being re-implemented in Java.
     */
    @Test
    void candidatesAreConstrainedByASpecificationNotFetchedById() {
        ProductFilterRequest filter = semanticFilter("fruity");
        stubCardMapping();
        when(aiSearchService.isSearchEnabled()).thenReturn(true);
        when(aiSearchService.semanticCandidates("fruity", 50)).thenReturn(Map.of(1L, 0.9));
        when(hybridRanker.blend(anyList(), anyMap())).thenReturn(Map.of(1L, 0.5));
        when(productRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(product(1L)));

        service.getAll(filter);

        verify(productRepository).findAll(any(Specification.class), any(Sort.class));
        verify(productRepository, never()).findAllByIdInWithDetails(anyList());
        verify(productRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    /**
     * The facet filters survive into the candidate query while the free-text predicate is
     * dropped: it matched the product <em>name</em>, which is exactly the match semantic search
     * exists to replace, so keeping it would collapse the feature back into keyword search.
     */
    @Test
    void facetFiltersConstrainTheCandidatesButTheKeywordTextDoesNot() {
        ProductFilterRequest filter = semanticFilter("fruity light roast");
        filter.setCategorySlug("single-origin");
        filter.setMinPrice(BigDecimal.valueOf(120));
        filter.setOptionValueIds(List.of(4L, 5L));
        filter.setInStockOnly(true);
        stubCardMapping();
        when(productOptionValueRepository.countDistinctOptionsByValueIds(List.of(4L, 5L)))
                .thenReturn(2L);
        when(aiSearchService.isSearchEnabled()).thenReturn(true);
        when(aiSearchService.semanticCandidates("fruity light roast", 50))
                .thenReturn(Map.of(1L, 0.9));
        when(hybridRanker.blend(anyList(), anyMap())).thenReturn(Map.of(1L, 0.5));
        when(productRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(product(1L)));

        try (MockedStatic<ProductSpecifications> specifications = mockStatic(ProductSpecifications.class)) {
            specifications.when(() -> ProductSpecifications.withFilters(any(), any(), anyLong()))
                    .thenReturn(noPredicates());

            service.getAll(filter);

            ArgumentCaptor<ProductFilterRequest> captured =
                    ArgumentCaptor.forClass(ProductFilterRequest.class);
            specifications.verify(() -> ProductSpecifications.withFilters(captured.capture(), any(),
                    eq(2L)));

            ProductFilterRequest facets = captured.getValue();
            assertThat(facets.getSearch()).isNull();
            assertThat(facets.getCategorySlug()).isEqualTo("single-origin");
            assertThat(facets.getMinPrice()).isEqualByComparingTo(BigDecimal.valueOf(120));
            assertThat(facets.getOptionValueIds()).containsExactly(4L, 5L);
            assertThat(facets.getInStockOnly()).isTrue();
        }
    }

    /** The caller's request object is never mutated to make it safe to reuse for the facets. */
    @Test
    void theOriginalFilterIsLeftUntouched() {
        ProductFilterRequest filter = semanticFilter("fruity light roast");
        filter.setCategorySlug("single-origin");
        stubCardMapping();
        when(aiSearchService.isSearchEnabled()).thenReturn(true);
        when(aiSearchService.semanticCandidates("fruity light roast", 50))
                .thenReturn(Map.of(1L, 0.9));
        when(hybridRanker.blend(anyList(), anyMap())).thenReturn(Map.of(1L, 0.5));
        when(productRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(product(1L)));

        try (MockedStatic<ProductSpecifications> specifications = mockStatic(ProductSpecifications.class)) {
            specifications.when(() -> ProductSpecifications.withFilters(any(), any(), anyLong()))
                    .thenReturn(noPredicates());
            service.getAll(filter);
        }

        assertThat(filter.getSearch()).isEqualTo("fruity light roast");
        assertThat(filter.getCategorySlug()).isEqualTo("single-origin");
    }

    // --- degradation ---------------------------------------------------------------------

    /** Ollama down, nothing indexed yet, or no hit at all: keyword search still answers. */
    @Test
    void semanticSearchWithNoVectorHitsFallsBackToTheKeywordPage() {
        ProductFilterRequest filter = semanticFilter("yirgacheffe");
        when(aiSearchService.isSearchEnabled()).thenReturn(true);
        when(aiSearchService.semanticCandidates("yirgacheffe", 50)).thenReturn(Map.of());
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        PageResponse<ProductCardResponse> page = service.getAll(filter);

        assertThat(page.getTotalElements()).isZero();
        verifyNoInteractions(hybridRanker);
        verify(productRepository, never()).findAll(any(Specification.class), any(Sort.class));
    }

    /**
     * Every candidate was excluded by a facet filter. Falling back beats rendering an empty page,
     * and it is safe precisely because the keyword path applies the very same filters.
     */
    @Test
    void semanticSearchFallsBackWhenEveryCandidateIsFilteredOut() {
        ProductFilterRequest filter = semanticFilter("fruity");
        filter.setCategorySlug("decaf");
        when(aiSearchService.isSearchEnabled()).thenReturn(true);
        when(aiSearchService.semanticCandidates("fruity", 50)).thenReturn(Map.of(1L, 0.9));
        when(productRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of());
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        PageResponse<ProductCardResponse> page = service.getAll(filter);

        assertThat(page.getTotalElements()).isZero();
        verifyNoInteractions(hybridRanker);
    }

    // --- paging --------------------------------------------------------------------------

    /**
     * The total is the size of the <em>filtered</em> candidate set, not the vector top-K: a
     * candidate dropped by a facet filter must not inflate the page count the client walks.
     */
    @Test
    void totalElementsCountsTheFilteredCandidatesNotTheVectorTopK() {
        ProductFilterRequest filter = semanticFilter("fruity");
        filter.setSize(2);
        stubCardMapping();
        when(aiSearchService.isSearchEnabled()).thenReturn(true);
        when(aiSearchService.semanticCandidates("fruity", 50))
                .thenReturn(Map.of(1L, 0.9, 2L, 0.8, 3L, 0.7));
        when(hybridRanker.blend(anyList(), anyMap()))
                .thenReturn(Map.of(1L, 0.5, 2L, 0.4, 3L, 0.3));
        when(productRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(product(1L), product(2L), product(3L)));

        PageResponse<ProductCardResponse> page = service.getAll(filter);

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getPage()).isZero();
        assertThat(page.getSize()).isEqualTo(2);
        assertThat(page.isFirst()).isTrue();
        assertThat(page.isLast()).isFalse();
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void laterPagesAreSlicedOutOfTheRanking() {
        ProductFilterRequest filter = semanticFilter("fruity");
        filter.setSize(2);
        filter.setPage(1);
        stubCardMapping();
        when(aiSearchService.isSearchEnabled()).thenReturn(true);
        when(aiSearchService.semanticCandidates("fruity", 50))
                .thenReturn(Map.of(1L, 0.9, 2L, 0.8, 3L, 0.7));
        when(hybridRanker.blend(anyList(), anyMap()))
                .thenReturn(Map.of(1L, 0.5, 2L, 0.4, 3L, 0.3));
        when(productRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(product(1L), product(2L), product(3L)));

        PageResponse<ProductCardResponse> page = service.getAll(filter);

        assertThat(page.getContent()).extracting(ProductCardResponse::getId).containsExactly(3L);
        assertThat(page.isFirst()).isFalse();
        assertThat(page.isLast()).isTrue();
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    /** A page past the end of the ranking is empty rather than an error. */
    @Test
    void aPageBeyondTheRankingIsEmpty() {
        ProductFilterRequest filter = semanticFilter("fruity");
        filter.setPage(9);
        stubCardMapping();
        when(aiSearchService.isSearchEnabled()).thenReturn(true);
        when(aiSearchService.semanticCandidates("fruity", 50)).thenReturn(Map.of(1L, 0.9));
        when(hybridRanker.blend(anyList(), anyMap())).thenReturn(Map.of(1L, 0.5));
        when(productRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(product(1L)));

        PageResponse<ProductCardResponse> page = service.getAll(filter);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.isLast()).isTrue();
    }

    /** Only the products that made the requested page are turned into cards. */
    @Test
    void onlyTheRequestedPageIsMappedToCards() {
        ProductFilterRequest filter = semanticFilter("fruity");
        filter.setSize(2);
        stubCardMapping();
        when(aiSearchService.isSearchEnabled()).thenReturn(true);
        when(aiSearchService.semanticCandidates("fruity", 50))
                .thenReturn(Map.of(1L, 0.9, 2L, 0.8, 3L, 0.7));
        when(hybridRanker.blend(anyList(), anyMap()))
                .thenReturn(Map.of(1L, 0.5, 2L, 0.4, 3L, 0.3));
        when(productRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(product(1L), product(2L), product(3L)));

        PageResponse<ProductCardResponse> page = service.getAll(filter);

        ArgumentCaptor<Product> mapped = ArgumentCaptor.forClass(Product.class);
        verify(productResponseMapper, times(2)).buildListResponse(mapped.capture(), anyString(), any());
        assertThat(mapped.getAllValues()).extracting(Product::getId).containsExactly(1L, 2L);
        assertThat(page.getContent()).hasSize(2);
    }

    /** A single-candidate ranking must not leave the page looking half-built. */
    @Test
    void aSingleCandidateStillReportsACompletePage() {
        ProductFilterRequest filter = semanticFilter("fruity");
        stubCardMapping();
        when(aiSearchService.isSearchEnabled()).thenReturn(true);
        when(aiSearchService.semanticCandidates("fruity", 50)).thenReturn(Map.of(1L, 0.9));
        when(hybridRanker.blend(anyList(), anyMap())).thenReturn(Map.of(1L, 0.5));
        when(productRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(product(1L)));

        PageResponse<ProductCardResponse> page = service.getAll(filter);

        assertThat(page.getTotalPages()).isEqualTo(1);
        assertThat(page.isFirst()).isTrue();
        assertThat(page.isLast()).isTrue();
    }
}