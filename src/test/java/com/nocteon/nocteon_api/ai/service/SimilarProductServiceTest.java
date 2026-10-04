package com.nocteon.nocteon_api.ai.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.i18n.LocaleContextHolder;

import com.nocteon.nocteon_api.ai.dto.response.SimilarProductResponse;
import com.nocteon.nocteon_api.common.exception.notFound.ProductNotFoundException;
import com.nocteon.nocteon_api.product.dto.response.ProductCardResponse;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.mapper.ProductResponseMapper;
import com.nocteon.nocteon_api.product.repository.ProductRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The three degradation paths are the point of this class, so they are pinned here by name:
 * feature off, store throws, and store answers with nothing usable. Each must return results.
 */
@ExtendWith(MockitoExtension.class)
class SimilarProductServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private AiSearchService aiSearchService;

    @Mock
    private ProductDocumentFactory documentFactory;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductResponseMapper productResponseMapper;

    @Mock
    private AttributeSimilarityFallback fallback;

    @InjectMocks
    private SimilarProductService service;

    /**
     * Pinned rather than inherited from {@code Locale.getDefault()} so the language handed to
     * {@link ProductResponseMapper} is asserted explicitly and does not change with the machine
     * the suite happens to run on.
     */
    @BeforeEach
    void pinLocale() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    private static Product product(long id, String slug) {
        return Product.builder().id(id).slug(slug).build();
    }

    /** A hit as Spring AI 2.0.1 returns one: {@code getScore()} already holds {@code 1 - distance}. */
    private static Document hit(long productId, double similarity) {
        return Document.builder()
                .id("product:p" + productId)
                .text("Yirgacheffe")
                .metadata(Map.of("productId", productId, "slug", "p" + productId))
                .score(similarity)
                .build();
    }

    private static ProductCardResponse card(long id) {
        return ProductCardResponse.builder().id(id).build();
    }

    private Product source() {
        return product(1L, "src");
    }

    // --- degradation path 1: the feature flag is off -----------------------------------

    @Test
    void returnsAttributeFallbackWhenSearchDisabled() {
        Product source = source();
        Product other = product(2L, "other");

        when(productRepository.findBySlug("src")).thenReturn(java.util.Optional.of(source));
        when(aiSearchService.isSearchEnabled()).thenReturn(false);
        when(productRepository.findAll()).thenReturn(List.of(source, other));
        when(fallback.rank(source, List.of(source, other))).thenReturn(Map.of(2L, 0.4));
        when(fallback.reason(source, other, 0.4)).thenReturn("Similar origin: Ethiopia");
        when(productResponseMapper.buildListResponse(other, "en")).thenReturn(card(2L));

        List<SimilarProductResponse> results = service.findSimilar("src", 8);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getReason()).isEqualTo("Similar origin: Ethiopia");
        verifyNoInteractions(vectorStore);
    }

    // --- degradation path 2: the vector store throws -----------------------------------

    @Test
    void returnsAttributeFallbackWhenVectorStoreThrows() {
        // Ollama down — must still return results, never propagate.
        Product source = source();
        Product other = product(2L, "other");

        when(productRepository.findBySlug("src")).thenReturn(java.util.Optional.of(source));
        when(aiSearchService.isSearchEnabled()).thenReturn(true);
        lenient().when(documentFactory.buildText(source)).thenReturn("Name: Source");
        when(productRepository.findAll()).thenReturn(List.of(source, other));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("ollama down"));
        when(fallback.rank(source, List.of(source, other))).thenReturn(Map.of(2L, 0.4));
        lenient().when(fallback.reason(any(), any(), anyDouble())).thenReturn("Similar origin: Ethiopia");
        when(productResponseMapper.buildListResponse(other, "en")).thenReturn(card(2L));

        List<SimilarProductResponse> results = service.findSimilar("src", 8);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getProduct()).isNotNull();
    }

    @Test
    void neverThrowsWhenTheEmbeddingModelIsUnreachable() {
        Product source = source();

        when(productRepository.findBySlug("src")).thenReturn(java.util.Optional.of(source));
        when(aiSearchService.isSearchEnabled()).thenReturn(true);
        lenient().when(documentFactory.buildText(source)).thenReturn("Name: Source");
        when(productRepository.findAll()).thenReturn(List.of(source, product(2L, "other")));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new IllegalStateException("connection refused"));
        lenient().when(fallback.rank(any(), any())).thenReturn(Map.of(2L, 0.4));
        lenient().when(fallback.reason(any(), any(), anyDouble())).thenReturn("reason");
        lenient().when(productResponseMapper.buildListResponse(any(), anyString())).thenReturn(card(2L));

        assertThatCode(() -> service.findSimilar("src", 8)).doesNotThrowAnyException();
    }

    // --- degradation path 3: the vector store answers with nothing usable --------------

    @Test
    void returnsAttributeFallbackWhenTheStoreAnswersWithNothingUsable() {
        Product source = source();
        Product other = product(2L, "other");

        when(productRepository.findBySlug("src")).thenReturn(java.util.Optional.of(source));
        when(aiSearchService.isSearchEnabled()).thenReturn(true);
        lenient().when(documentFactory.buildText(source)).thenReturn("Name: Source");
        // Nothing indexed yet: the only hit is the source's own document.
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(hit(1L, 0.99)));
        when(productRepository.findAll()).thenReturn(List.of(source, other));
        when(fallback.rank(source, List.of(source, other))).thenReturn(Map.of(2L, 0.4));
        lenient().when(fallback.reason(any(), any(), anyDouble())).thenReturn("reason");
        when(productResponseMapper.buildListResponse(other, "en")).thenReturn(card(2L));

        List<SimilarProductResponse> results = service.findSimilar("src", 8);

        assertThat(results).hasSize(1);
        verify(productRepository, never()).findAllByIdInWithDetails(anyList());
    }

    // --- vector path -------------------------------------------------------------------

    /**
     * The inversion trap: Spring AI 2.0.1's {@code DocumentRowMapper} already stores
     * {@code 1 - distance} on {@link Document#getScore()}. Converting again would promote the
     * worst match, so the score must reach the response untouched and order the hits.
     */
    @Test
    void ranksVectorHitsByTheirRawSimilarityWithoutASecondConversion() {
        Product source = source();
        Product best = product(2L, "best");
        Product worst = product(3L, "worst");

        when(productRepository.findBySlug("src")).thenReturn(java.util.Optional.of(source));
        when(aiSearchService.isSearchEnabled()).thenReturn(true);
        lenient().when(documentFactory.buildText(source)).thenReturn("Name: Source");
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(hit(3L, 0.23), hit(2L, 0.91)));
        when(productRepository.findAllByIdInWithDetails(anyList())).thenReturn(List.of(best, worst));
        lenient().when(fallback.reason(any(), any(), anyDouble())).thenReturn("reason");
        when(productResponseMapper.buildListResponse(best, "en")).thenReturn(card(2L));
        lenient().when(productResponseMapper.buildListResponse(worst, "en")).thenReturn(card(3L));

        List<SimilarProductResponse> results = service.findSimilar("src", 8);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getScore()).isEqualTo(0.91);
        assertThat(results.get(1).getScore()).isEqualTo(0.23);
        assertThat(results.get(0).getProduct().getId()).isEqualTo(2L);
        assertThat(results.get(1).getProduct().getId()).isEqualTo(3L);
        // The vector path answered, so the attribute fallback was never consulted.
        verify(fallback, never()).rank(any(), anyList());
    }

    @Test
    void excludesTheSourceProductFromVectorHits() {
        Product source = source();
        Product other = product(2L, "other");

        when(productRepository.findBySlug("src")).thenReturn(java.util.Optional.of(source));
        when(aiSearchService.isSearchEnabled()).thenReturn(true);
        lenient().when(documentFactory.buildText(source)).thenReturn("Name: Source");
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(hit(1L, 1.0), hit(2L, 0.5)));
        when(productRepository.findAllByIdInWithDetails(anyList())).thenReturn(List.of(other));
        lenient().when(fallback.reason(any(), any(), anyDouble())).thenReturn("reason");
        lenient().when(productResponseMapper.buildListResponse(other, "en")).thenReturn(card(2L));

        List<SimilarProductResponse> results = service.findSimilar("src", 8);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getProduct().getId()).isEqualTo(2L);

        ArgumentCaptor<List<Long>> ids = ArgumentCaptor.forClass(List.class);
        verify(productRepository).findAllByIdInWithDetails(ids.capture());
        assertThat(ids.getValue()).containsExactly(2L);
    }

    /**
     * The source's own text is its own nearest neighbour, so the store is asked for one extra
     * hit: otherwise the best real match is dropped before the source is filtered out.
     */
    @Test
    void asksTheStoreForTheSourceProductTextWithOneExtraHitOfHeadroom() {
        Product source = source();
        Product other = product(2L, "other");

        when(productRepository.findBySlug("src")).thenReturn(java.util.Optional.of(source));
        when(aiSearchService.isSearchEnabled()).thenReturn(true);
        when(documentFactory.buildText(source)).thenReturn("Name: Source\nOrigin: ethiopia");
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(hit(2L, 0.5)));
        lenient().when(productRepository.findAllByIdInWithDetails(anyList())).thenReturn(List.of(other));
        lenient().when(fallback.reason(any(), any(), anyDouble())).thenReturn("reason");
        lenient().when(productResponseMapper.buildListResponse(other, "en")).thenReturn(card(2L));

        service.findSimilar("src", 8);

        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(request.capture());
        assertThat(request.getValue().getQuery()).isEqualTo("Name: Source\nOrigin: ethiopia");
        assertThat(request.getValue().getTopK()).isEqualTo(9);
    }

    @Test
    void carriesANonBlankReasonOnEveryVectorResult() {
        Product source = source();
        Product other = product(2L, "other");

        when(productRepository.findBySlug("src")).thenReturn(java.util.Optional.of(source));
        when(aiSearchService.isSearchEnabled()).thenReturn(true);
        lenient().when(documentFactory.buildText(source)).thenReturn("text");
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(hit(2L, 0.5)));
        lenient().when(productRepository.findAllByIdInWithDetails(anyList())).thenReturn(List.of(other));
        lenient().when(fallback.reason(any(), any(), anyDouble())).thenReturn("");
        lenient().when(productResponseMapper.buildListResponse(other, "en")).thenReturn(card(2L));

        assertThat(service.findSimilar("src", 8))
                .isNotEmpty()
                .allSatisfy(result -> assertThat(result.getReason()).isNotBlank());
    }

    @Test
    void skipsHitsWithoutAScoreOrANumericProductId() {
        Product source = source();
        Product other = product(2L, "other");
        Document noScore = Document.builder().id("product:x").text("t").metadata(Map.of("productId", 3L)).build();
        Document badId = Document.builder().id("product:y").text("t")
                .metadata(Map.of("productId", "nope")).score(0.9).build();

        when(productRepository.findBySlug("src")).thenReturn(java.util.Optional.of(source));
        when(aiSearchService.isSearchEnabled()).thenReturn(true);
        lenient().when(documentFactory.buildText(source)).thenReturn("text");
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(noScore, badId, hit(2L, 0.5)));
        when(productRepository.findAllByIdInWithDetails(anyList())).thenReturn(List.of(other));
        lenient().when(fallback.reason(any(), any(), anyDouble())).thenReturn("reason");
        lenient().when(productResponseMapper.buildListResponse(other, "en")).thenReturn(card(2L));

        List<SimilarProductResponse> results = service.findSimilar("src", 8);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getProduct().getId()).isEqualTo(2L);
    }

    // --- limit and filtering ----------------------------------------------------------

    @Test
    void clampsLimitToSensibleRange() {
        Product source = source();

        when(productRepository.findBySlug("src")).thenReturn(java.util.Optional.of(source));
        when(aiSearchService.isSearchEnabled()).thenReturn(false);
        when(productRepository.findAll()).thenReturn(List.of(source));
        lenient().when(fallback.rank(any(), any())).thenReturn(Map.of());
        lenient().when(productResponseMapper.buildListResponse(any(), anyString()))
                .thenReturn(ProductCardResponse.builder().build());

        assertThat(service.findSimilar("src", 500)).hasSizeLessThanOrEqualTo(20);
    }

    @Test
    void clampsAZeroOrNegativeLimitUpToOne() {
        Product source = source();
        Product other = product(2L, "other");

        when(productRepository.findBySlug("src")).thenReturn(java.util.Optional.of(source));
        when(aiSearchService.isSearchEnabled()).thenReturn(false);
        when(productRepository.findAll()).thenReturn(List.of(source, other));
        when(fallback.rank(any(), any())).thenReturn(Map.of(2L, 0.4));
        lenient().when(fallback.reason(any(), any(), anyDouble())).thenReturn("reason");
        lenient().when(productResponseMapper.buildListResponse(other, "en")).thenReturn(card(2L));

        assertThat(service.findSimilar("src", 0)).hasSize(1);
        assertThat(service.findSimilar("src", -7)).hasSize(1);
    }

    @Test
    void truncatesTheAttributeRankingToTheRequestedLimit() {
        Product source = source();
        List<Product> catalogue = java.util.stream.IntStream.rangeClosed(2, 40)
                .mapToObj(i -> product((long) i, "p" + i))
                .toList();
        Map<Long, Double> scores = new java.util.LinkedHashMap<>();
        catalogue.forEach(p -> scores.put(p.getId(), 1.0 / p.getId()));

        when(productRepository.findBySlug("src")).thenReturn(java.util.Optional.of(source));
        when(aiSearchService.isSearchEnabled()).thenReturn(false);
        when(productRepository.findAll()).thenReturn(catalogue);
        when(fallback.rank(any(), any())).thenReturn(scores);
        lenient().when(fallback.reason(any(), any(), anyDouble())).thenReturn("reason");
        lenient().when(productResponseMapper.buildListResponse(any(), anyString()))
                .thenAnswer(invocation -> card(invocation.getArgument(0, Product.class).getId()));

        List<SimilarProductResponse> results = service.findSimilar("src", 5);

        assertThat(results).hasSize(5);
        assertThat(results).extracting(SimilarProductResponse::getScore)
                .containsExactly(0.5, 1.0 / 3, 1.0 / 4, 1.0 / 5, 1.0 / 6);
    }

    /** A public storefront rail must never surface a product that is not on sale. */
    @Test
    void skipsInactiveCandidatesOnTheAttributePath() {
        Product source = source();
        Product inactive = Product.builder().id(2L).slug("other").isActive(false).build();
        Product active = product(3L, "third");

        when(productRepository.findBySlug("src")).thenReturn(java.util.Optional.of(source));
        when(aiSearchService.isSearchEnabled()).thenReturn(false);
        when(productRepository.findAll()).thenReturn(List.of(source, inactive, active));
        when(fallback.rank(any(), eq(List.of(source, active)))).thenReturn(Map.of(3L, 0.4));
        lenient().when(fallback.reason(any(), any(), anyDouble())).thenReturn("reason");
        lenient().when(productResponseMapper.buildListResponse(active, "en")).thenReturn(card(3L));

        List<SimilarProductResponse> results = service.findSimilar("src", 8);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getProduct().getId()).isEqualTo(3L);
    }

    @Test
    void skipsInactiveProductsOnTheVectorPathToo() {
        Product source = source();
        Product inactive = Product.builder().id(2L).slug("other").isActive(false).build();

        when(productRepository.findBySlug("src")).thenReturn(java.util.Optional.of(source));
        when(aiSearchService.isSearchEnabled()).thenReturn(true);
        lenient().when(documentFactory.buildText(source)).thenReturn("text");
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(hit(2L, 0.8), hit(3L, 0.4)));
        lenient().when(productRepository.findAllByIdInWithDetails(anyList()))
                .thenReturn(List.of(inactive, product(3L, "third")));
        lenient().when(fallback.rank(any(), anyList())).thenReturn(Map.of());
        lenient().when(fallback.reason(any(), any(), anyDouble())).thenReturn("reason");
        lenient().when(productResponseMapper.buildListResponse(any(), anyString()))
                .thenAnswer(invocation -> card(invocation.getArgument(0, Product.class).getId()));

        List<SimilarProductResponse> results = service.findSimilar("src", 8);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getProduct().getId()).isEqualTo(3L);
    }

    // --- unknown slug ------------------------------------------------------------------

    @Test
    void unknownSlugThrowsProductNotFound() {
        when(productRepository.findBySlug("nope")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.findSimilar("nope", 8))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void anUnknownSlugNeverReachesTheVectorStoreOrTheFallback() {
        when(productRepository.findBySlug("nope")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.findSimilar("nope", 8))
                .isInstanceOf(ProductNotFoundException.class);

        verifyNoInteractions(vectorStore, aiSearchService, fallback, productResponseMapper);
    }

    // --- response contract -------------------------------------------------------------

    @Test
    void mapsTheCardThroughTheResponseMapperForTheRequestLanguage() {
        Product source = source();
        Product other = product(2L, "other");

        LocaleContextHolder.setLocale(new Locale("ar"));
        when(productRepository.findBySlug("src")).thenReturn(java.util.Optional.of(source));
        when(aiSearchService.isSearchEnabled()).thenReturn(false);
        when(productRepository.findAll()).thenReturn(List.of(source, other));
        lenient().when(fallback.rank(any(), any())).thenReturn(Map.of(2L, 0.4));
        lenient().when(fallback.reason(any(), any(), anyDouble())).thenReturn("reason");
        lenient().when(productResponseMapper.buildListResponse(other, "ar")).thenReturn(card(2L));

        service.findSimilar("src", 8);

        verify(productResponseMapper).buildListResponse(other, "ar");
    }

    @Test
    void anEmptyCatalogueYieldsAnEmptyListRatherThanAnError() {
        Product source = source();

        when(productRepository.findBySlug("src")).thenReturn(java.util.Optional.of(source));
        when(aiSearchService.isSearchEnabled()).thenReturn(false);
        when(productRepository.findAll()).thenReturn(List.of(source));
        when(fallback.rank(any(), any())).thenReturn(Map.of());

        assertThat(service.findSimilar("src", 8)).isEmpty();
    }
}