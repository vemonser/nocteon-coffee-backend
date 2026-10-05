package com.nocteon.nocteon_api.ai.service;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;

import com.nocteon.nocteon_api.ai.config.AiProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSearchServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ProductIndexingService indexingService;

    @Mock
    private AiProperties properties;

    @InjectMocks
    private AiSearchService service;

    /**
     * Stubs both flags leniently, and that is deliberate rather than lazy.
     *
     * <p>{@code isSearchEnabled()} short-circuits on {@code &&}, so whenever {@code enabled} is
     * false the {@code searchEnabled} stub is never read — and under Mockito's default strict
     * stubs an unread stub fails the test. Every combination is exercised here, so the
     * unread-stub failures are pure noise rather than a signal about the code.
     */
    private void flags(boolean enabled, boolean searchEnabled) {
        lenient().when(properties.isEnabled()).thenReturn(enabled);
        lenient().when(properties.isSearchEnabled()).thenReturn(searchEnabled);
    }

    /**
     * A hit as Spring AI 2.0.1 actually returns one: {@link Document#getScore()} already
     * holds {@code 1 - distance}, i.e. a similarity in {@code [0,1]} — never a distance.
     */
    private static Document hit(long productId, double similarity) {
        return Document.builder()
                .id("product:p" + productId)
                .text("Yirgacheffe")
                .metadata(Map.of("productId", productId, "slug", "p" + productId))
                .score(similarity)
                .build();
    }

    // --- graceful degradation ---------------------------------------------------------

    @Test
    void returnsEmptyWhenDisabled() {
        flags(false, false);

        assertThat(service.semanticCandidates("fruity light roast", 20)).isEmpty();
        verifyNoInteractions(vectorStore);
    }

    @Test
    void returnsEmptyForBlankQuery() {
        // Review Focus item 1: never embed blank text.
        flags(true, true);

        assertThat(service.semanticCandidates("   ", 20)).isEmpty();
        verifyNoInteractions(vectorStore);
    }

    @Test
    void returnsEmptyForNullQuery() {
        flags(true, true);

        assertThat(service.semanticCandidates(null, 20)).isEmpty();
        verifyNoInteractions(vectorStore);
    }

    @Test
    void returnsEmptyWhenOllamaThrows() {
        flags(true, true);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("ollama unreachable"));

        assertThat(service.semanticCandidates("fruity", 20)).isEmpty();
    }

    @Test
    void neverThrowsOnAnUnreachableVectorStore() {
        flags(true, true);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new IllegalStateException("connection refused"));

        assertThatCode(() -> service.semanticCandidates("fruity", 20)).doesNotThrowAnyException();
    }

    @Test
    void returnsEmptyWhenSearchFlagIsOffEvenIfMasterIsOn() {
        flags(true, false);

        assertThat(service.semanticCandidates("fruity", 20)).isEmpty();
        verifyNoInteractions(vectorStore);
    }

    @Test
    void returnsEmptyWhenNoHitsComeBack() {
        flags(true, true);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        assertThat(service.semanticCandidates("fruity", 20)).isEmpty();
    }

    // --- score mapping ----------------------------------------------------------------

    @Test
    void mapsProductIdFromMetadataToScore() {
        flags(true, true);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(hit(42L, 0.75)));

        Map<Long, Double> scores = service.semanticCandidates("fruity", 20);

        assertThat(scores).containsKey(42L);
    }

    /**
     * The brief prescribed {@code 1.0 - score}, but Spring AI 2.0.1 already stores
     * {@code 1 - distance} on {@link Document#getScore()}. Applying the conversion again
     * would invert the ranking, so the score is used as-is.
     */
    @Test
    void scoreIsUsedAsSimilarityWithoutASecondConversion() {
        flags(true, true);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(hit(42L, 0.75)));

        Map<Long, Double> scores = service.semanticCandidates("fruity", 20);

        assertThat(scores.get(42L)).isEqualTo(0.75);
    }

    @Test
    void betterMatchScoresHigher() {
        flags(true, true);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(hit(1L, 0.90), hit(2L, 0.30)));

        Map<Long, Double> scores = service.semanticCandidates("fruity", 20);

        assertThat(scores.get(1L)).isGreaterThan(scores.get(2L));
    }

    @Test
    void skipsHitsWithoutAProductId() {
        flags(true, true);
        Document noId = Document.builder().id("product:x").text("t").score(0.9).build();
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(noId, hit(7L, 0.5)));

        Map<Long, Double> scores = service.semanticCandidates("fruity", 20);

        assertThat(scores).containsOnlyKeys(7L);
    }

    @Test
    void skipsHitsWhoseProductIdIsNotANumber() {
        flags(true, true);
        Document badId = Document.builder()
                .id("product:x").text("t")
                .metadata(Map.of("productId", "not-a-number"))
                .score(0.9).build();
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(badId));

        assertThat(service.semanticCandidates("fruity", 20)).isEmpty();
    }

    @Test
    void skipsHitsWithNoScore() {
        flags(true, true);
        Document noScore = Document.builder()
                .id("product:x").text("t")
                .metadata(Map.of("productId", 5L))
                .build();
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(noScore, hit(6L, 0.5)));

        assertThat(service.semanticCandidates("fruity", 20)).containsOnlyKeys(6L);
    }

    @Test
    void keepsTheBestScoreWhenAProductAppearsTwice() {
        flags(true, true);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(hit(9L, 0.4), hit(9L, 0.8)));

        Map<Long, Double> scores = service.semanticCandidates("fruity", 20);

        assertThat(scores).containsOnlyKeys(9L);
        assertThat(scores.get(9L)).isEqualTo(0.8);
    }

    // --- request shape ----------------------------------------------------------------

    @Test
    void passesQueryAndTopKThroughToTheVectorStore() {
        flags(true, true);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        service.semanticCandidates("fruity light roast", 17);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getQuery())
                .isEqualTo(EmbeddingTaskPrefix.QUERY + "fruity light roast");
        assertThat(captor.getValue().getTopK()).isEqualTo(17);
    }

    /**
     * The query carries the query-side retrieval prefix, and the user's own words follow it
     * unchanged.
     *
     * <p>{@code gemini-embedding-2} has no {@code task_type} parameter, so the retrieval intent
     * has to be in the text. The prefix is what makes a query a query rather than a document, and
     * {@code ProductDocumentFactoryTest} asserts the document side is prefixed differently — the two
     * halves of one pair, asserted from both ends so they cannot drift apart.
     */
    @Test
    void prefixesTheQueryWithTheRetrievalIntent() {
        flags(true, true);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        service.semanticCandidates("fruity light roast", 20);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getQuery())
                .startsWith("task: search result | query: ")
                .endsWith("fruity light roast")
                .isNotEqualTo(EmbeddingTaskPrefix.DOCUMENT + "fruity light roast");
    }

    /**
     * A Gemini outage, a rate limit, or a wrong key must degrade exactly like a downed Ollama:
     * empty candidates, keyword ranking continues, no exception reaches the caller.
     *
     * <p>The exception is the one shape a hosted provider actually produces rather than a
     * connection refusal, and it is thrown from the embedding call the vector store makes
     * internally — which is why it has to be simulated by a store that embeds before searching.
     * {@code @InjectMocks} builds the service with the mocked store, so this exercises the real
     * {@code semanticCandidates} guard rather than a stand-in for it.
     */
    @Test
    void degradesToKeywordRankingWhenTheHostedEmbeddingModelFails() {
        flags(true, true);
        VectorStore geminiBacked = SimpleVectorStore.builder(new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                throw new IllegalStateException("Gemini API 429 RESOURCE_EXHAUSTED");
            }

            @Override
            public float[] embed(Document document) {
                throw new IllegalStateException("Gemini API 429 RESOURCE_EXHAUSTED");
            }
        }).build();

        AiSearchService hosted = new AiSearchService(geminiBacked, indexingService, properties);

        assertThatCode(() -> hosted.semanticCandidates("fruity light roast", 20))
                .doesNotThrowAnyException();
        assertThat(hosted.semanticCandidates("fruity light roast", 20)).isEmpty();
    }

    /**
     * Cosine similarity is not calibrated across embedding models, so no absolute cutoff
     * can be justified; the threshold stays at zero and ranking decides.
     */
    @Test
    void doesNotApplyAnAbsoluteSimilarityCutoff() {
        flags(true, true);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        service.semanticCandidates("fruity", 20);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getSimilarityThreshold()).isEqualTo(0.0);
    }

    // --- flags ------------------------------------------------------------------------

    @Test
    void isSearchEnabledRequiresBothFlags() {
        flags(true, false);
        assertThat(service.isSearchEnabled()).isFalse();

        flags(false, true);
        assertThat(service.isSearchEnabled()).isFalse();

        flags(true, true);
        assertThat(service.isSearchEnabled()).isTrue();
    }

    @Test
    void isSearchEnabledIsFalseWhenBothFlagsAreOff() {
        flags(false, false);

        assertThat(service.isSearchEnabled()).isFalse();
    }

    // --- delegates --------------------------------------------------------------------

    @Test
    void reindexAllDelegatesToTheIndexingService() {
        lenient().when(indexingService.indexAll())
                .thenReturn(com.nocteon.nocteon_api.ai.dto.response.AiReindexResponse.builder()
                        .indexed(3).skipped(4).durationMs(5).build());

        assertThat(service.reindexAll().getIndexed()).isEqualTo(3);
        verify(indexingService).indexAll();
    }

    @Test
    void indexStatsDelegatesToTheIndexingService() {
        lenient().when(indexingService.stats())
                .thenReturn(com.nocteon.nocteon_api.ai.dto.response.AiIndexStatsResponse.builder()
                        .indexed(1).total(2).stale(1).build());

        assertThat(service.indexStats().getStale()).isEqualTo(1);
        verify(indexingService).stats();
    }

    /**
     * The admin delegates are not flag-guarded: an operator must be able to inspect, and
     * repair, a store that the search flag switched off in the first place. Authorization is
     * the calling controller's {@code @PreAuthorize}, not a flag.
     */
    @Test
    void delegatesDoNotDependOnAnyFeatureFlag() {
        // No flags stubbed: reading either one would NPE on the mock's default null/false and
        // is exactly what this test rules out.
        lenient().when(indexingService.indexAll())
                .thenReturn(com.nocteon.nocteon_api.ai.dto.response.AiReindexResponse.builder().build());
        lenient().when(indexingService.stats())
                .thenReturn(com.nocteon.nocteon_api.ai.dto.response.AiIndexStatsResponse.builder().build());

        assertThatCode(() -> service.reindexAll()).doesNotThrowAnyException();
        assertThatCode(() -> service.indexStats()).doesNotThrowAnyException();
        verify(indexingService).indexAll();
        verify(indexingService).stats();
    }
}
