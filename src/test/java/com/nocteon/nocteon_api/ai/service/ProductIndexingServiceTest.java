package com.nocteon.nocteon_api.ai.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.ai.dto.response.AiIndexStatsResponse;
import com.nocteon.nocteon_api.ai.dto.response.AiReindexResponse;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.product.repository.ProductRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductIndexingServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductDocumentFactory documentFactory;

    @InjectMocks
    private ProductIndexingService service;

    private static Product product(Long id, String slug) {
        return Product.builder().id(id).slug(slug).build();
    }

    private static Document document(long id, String slug, String text, String contentHash) {
        return new Document("product:" + slug, text,
                Map.of("productId", id, "slug", slug, "contentHash", contentHash));
    }

    /** A product whose translations land in the {@code HashSet} in the given insertion order. */
    private static Product productWithTranslations(Long id, String slug, String... languages) {
        Set<ProductTranslation> translations = new HashSet<>();
        for (String language : languages) {
            translations.add(ProductTranslation.builder()
                    .language(language)
                    .name("Name " + language)
                    .shortDescription("Description " + language)
                    .build());
        }

        return Product.builder()
                .id(id)
                .slug(slug)
                .productType(ProductType.COFFEE)
                .translations(translations)
                .build();
    }

    @Test
    void indexAllUpsertsEveryActiveProduct() {
        Product a = product(1L, "a");
        Product b = product(2L, "b");
        Document docA = document(1L, "a", "text a", "h1");
        Document docB = document(2L, "b", "text b", "h2");

        when(productRepository.findAll()).thenReturn(List.of(a, b));
        when(productRepository.findAllByIdInWithDetails(List.of(1L, 2L))).thenReturn(List.of(a, b));
        when(documentFactory.toDocument(a)).thenReturn(docA);
        when(documentFactory.toDocument(b)).thenReturn(docB);

        AiReindexResponse result = service.indexAll();

        verify(vectorStore).add(List.of(docA, docB));
        assertThat(result.getIndexed()).isEqualTo(2);
        assertThat(result.getSkipped()).isZero();
        assertThat(result.getDurationMs()).isNotNegative();
    }

    @Test
    void indexAllSkipsProductWhoseHashIsUnchanged() {
        Document existing = document(1L, "a", "old", "same");
        Document fresh = document(1L, "a", "new", "same");
        Product a = product(1L, "a");

        when(productRepository.findAll()).thenReturn(List.of(a));
        when(productRepository.findAllByIdInWithDetails(List.of(1L))).thenReturn(List.of(a));
        when(documentFactory.toDocument(a)).thenReturn(fresh);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(existing));

        AiReindexResponse result = service.indexAll();

        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(result.getIndexed()).isZero();
        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void skipCheckLooksTheStoredHashUpAgainstTheProductsOwnText() {
        // Spring AI 2.0.1 has no get-by-id, so the hash is read back by searching with the
        // document's own text and asking for a single hit.
        Product a = product(1L, "a");
        Document fresh = document(1L, "a", "fresh text", "h1");

        when(productRepository.findAll()).thenReturn(List.of(a));
        when(productRepository.findAllByIdInWithDetails(List.of(1L))).thenReturn(List.of(a));
        when(documentFactory.toDocument(a)).thenReturn(fresh);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        service.indexAll();

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getQuery()).isEqualTo("fresh text");
        assertThat(captor.getValue().getTopK()).isEqualTo(1);
    }

    @Test
    void indexAllReembedsWhenTheStoredDocumentBelongsToAnotherProduct() {
        // A different product's document must never satisfy the skip check, even when the
        // hash strings happen to collide.
        Product a = product(1L, "a");
        Document fresh = document(1L, "a", "text a", "h1");
        Document somebodyElse = document(9L, "b", "text a", "h1");

        when(productRepository.findAll()).thenReturn(List.of(a));
        when(productRepository.findAllByIdInWithDetails(List.of(1L))).thenReturn(List.of(a));
        when(documentFactory.toDocument(a)).thenReturn(fresh);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(somebodyElse));

        AiReindexResponse result = service.indexAll();

        assertThat(result.getIndexed()).isEqualTo(1);
        assertThat(result.getSkipped()).isZero();
        verify(vectorStore).add(List.of(fresh));
    }

    @Test
    void indexAllReembedsWhenTheStoredDocumentCarriesNoHash() {
        // Two absent hashes must not compare as equal, or every unhashed row looks current.
        Product a = product(1L, "a");
        Document fresh = document(1L, "a", "text a", "h1");
        Document unhashed = new Document("product:a", "text a", Map.of("slug", "a"));

        when(productRepository.findAll()).thenReturn(List.of(a));
        when(productRepository.findAllByIdInWithDetails(List.of(1L))).thenReturn(List.of(a));
        when(documentFactory.toDocument(a)).thenReturn(fresh);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(unhashed));

        AiReindexResponse result = service.indexAll();

        assertThat(result.getIndexed()).isEqualTo(1);
        assertThat(result.getSkipped()).isZero();
        verify(vectorStore).add(List.of(fresh));
    }

    @Test
    void indexAllIgnoresInactiveProducts() {
        Product active = product(1L, "a");
        Product inactive = product(2L, "b");
        inactive.setActive(false);
        Document docActive = document(1L, "a", "text a", "h1");

        when(productRepository.findAll()).thenReturn(List.of(active, inactive));
        when(productRepository.findAllByIdInWithDetails(List.of(1L))).thenReturn(List.of(active));
        when(documentFactory.toDocument(active)).thenReturn(docActive);

        AiReindexResponse result = service.indexAll();

        assertThat(result.getIndexed()).isEqualTo(1);
        verify(vectorStore).add(List.of(docActive));
        verify(documentFactory, never()).toDocument(inactive);
    }

    @Test
    void indexAllTouchesNeitherTheStoreNorTheDetailQueryWhenNoProductIsActive() {
        // Guards the `id IN ()` query, which is invalid SQL.
        when(productRepository.findAll()).thenReturn(List.of());

        AiReindexResponse result = service.indexAll();

        assertThat(result.getIndexed()).isZero();
        assertThat(result.getSkipped()).isZero();
        verify(productRepository, never()).findAllByIdInWithDetails(anyList());
        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void removeDeletesByStableDocumentId() {
        service.remove("a");

        verify(vectorStore).delete(List.of("product:a"));
    }

    @Test
    void removeIgnoresBlankSlug() {
        service.remove("  ");

        verify(vectorStore, never()).delete(anyList());
    }

    @Test
    void statsCountsStaleWhenStoredHashDiffers() {
        Product a = product(1L, "a");
        Document fresh = document(1L, "a", "text a", "h-new");
        Document stored = document(1L, "a", "text a", "h-old");

        when(productRepository.findAll()).thenReturn(List.of(a));
        when(productRepository.findAllByIdInWithDetails(List.of(1L))).thenReturn(List.of(a));
        when(documentFactory.toDocument(a)).thenReturn(fresh);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(stored));

        AiIndexStatsResponse stats = service.stats();

        assertThat(stats.getTotal()).isEqualTo(1);
        assertThat(stats.getStale()).isEqualTo(1);
        assertThat(stats.getIndexed()).isZero();
    }

    @Test
    void statsCountsStaleWhenProductIsAbsentFromTheStore() {
        Product a = product(1L, "a");

        when(productRepository.findAll()).thenReturn(List.of(a));
        when(productRepository.findAllByIdInWithDetails(List.of(1L))).thenReturn(List.of(a));
        when(documentFactory.toDocument(a)).thenReturn(document(1L, "a", "text a", "h1"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        AiIndexStatsResponse stats = service.stats();

        assertThat(stats.getTotal()).isEqualTo(1);
        assertThat(stats.getStale()).isEqualTo(1);
        assertThat(stats.getIndexed()).isZero();
    }

    @Test
    void statsCountsProductAsIndexedWhenStoredHashMatches() {
        Product a = product(1L, "a");

        when(productRepository.findAll()).thenReturn(List.of(a));
        when(productRepository.findAllByIdInWithDetails(List.of(1L))).thenReturn(List.of(a));
        when(documentFactory.toDocument(a)).thenReturn(document(1L, "a", "text a", "h1"));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(document(1L, "a", "text a", "h1")));

        AiIndexStatsResponse stats = service.stats();

        assertThat(stats.getTotal()).isEqualTo(1);
        assertThat(stats.getIndexed()).isEqualTo(1);
        assertThat(stats.getStale()).isZero();
    }

    @Test
    void statsTreatsDifferingAndMissingHashesAsStale() {
        // Product 'a' is stored under an older hash, product 'b' is missing from the store
        // altogether. Both are stale under the "current hash is absent from the store"
        // definition, so the numbers are total 2 / stale 2 / indexed 0.
        Product a = product(1L, "a");
        Product b = product(2L, "b");

        when(productRepository.findAll()).thenReturn(List.of(a, b));
        when(productRepository.findAllByIdInWithDetails(List.of(1L, 2L))).thenReturn(List.of(a, b));
        when(documentFactory.toDocument(a)).thenReturn(document(1L, "a", "text a", "h-new"));
        when(documentFactory.toDocument(b)).thenReturn(document(2L, "b", "text b", "h-b"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            return "text a".equals(request.getQuery())
                    ? List.of(document(1L, "a", "text a", "h-old"))
                    : List.of();
        });

        AiIndexStatsResponse stats = service.stats();

        assertThat(stats.getTotal()).isEqualTo(2);
        assertThat(stats.getStale()).isEqualTo(2);
        assertThat(stats.getIndexed()).isZero();
    }

    @Test
    void statsReportsNullLastRunAtBeforeAnyRun() {
        when(productRepository.findAll()).thenReturn(List.of());

        assertThat(service.stats().getLastRunAt()).isNull();
    }

    @Test
    void statsReportsTheTimestampOfTheLastRun() {
        when(productRepository.findAll()).thenReturn(List.of(product(1L, "a")));
        when(productRepository.findAllByIdInWithDetails(List.of(1L)))
                .thenReturn(List.of(product(1L, "a")));
        when(documentFactory.toDocument(any(Product.class))).thenReturn(document(1L, "a", "text a", "h1"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        assertThat(service.lastRunAt).isNull();
        service.indexAll();

        assertThat(service.lastRunAt).isNotNull();
        assertThat(service.stats().getLastRunAt()).isEqualTo(service.lastRunAt);
    }

    @Test
    void lastRunAtIsSetEvenWhenTheRunFails() {
        when(productRepository.findAll()).thenReturn(List.of(product(1L, "a")));
        when(productRepository.findAllByIdInWithDetails(List.of(1L)))
                .thenReturn(List.of(product(1L, "a")));
        when(documentFactory.toDocument(any(Product.class))).thenReturn(document(1L, "a", "text a", "h1"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new IllegalStateException("ollama down"))
                .when(vectorStore).add(anyList());

        assertThatThrownBy(() -> service.indexAll())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ollama down");

        assertThat(service.lastRunAt).isNotNull();
    }

    @Test
    void aFailedRunDoesNotWedgeTheNextOne() {
        when(productRepository.findAll()).thenReturn(List.of(product(1L, "a")));
        when(productRepository.findAllByIdInWithDetails(List.of(1L)))
                .thenReturn(List.of(product(1L, "a")));
        when(documentFactory.toDocument(any(Product.class))).thenReturn(document(1L, "a", "text a", "h1"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new IllegalStateException("ollama down"))
                .doNothing()
                .when(vectorStore).add(anyList());

        assertThatThrownBy(() -> service.indexAll()).isInstanceOf(IllegalStateException.class);

        assertThat(service.indexAll().getIndexed()).isEqualTo(1);
    }

    @Test
    void aConcurrentRunIsRejectedInsteadOfOverlapping() throws Exception {
        CountDownLatch insideFirstRun = new CountDownLatch(1);
        CountDownLatch releaseFirstRun = new CountDownLatch(1);
        AtomicReference<AiReindexResponse> firstRunResult = new AtomicReference<>();
        AtomicReference<Throwable> firstRunFailure = new AtomicReference<>();

        Document docA = document(1L, "a", "text a", "h1");
        when(productRepository.findAll()).thenReturn(List.of(product(1L, "a")));
        when(productRepository.findAllByIdInWithDetails(List.of(1L)))
                .thenReturn(List.of(product(1L, "a")));
        when(documentFactory.toDocument(any(Product.class))).thenReturn(docA);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenAnswer(invocation -> {
            insideFirstRun.countDown();
            assertThat(releaseFirstRun.await(5, TimeUnit.SECONDS)).isTrue();
            return List.of();
        });

        Thread first = new Thread(() -> {
            try {
                firstRunResult.set(service.indexAll());
            } catch (Throwable t) {
                firstRunFailure.set(t);
            }
        });
        first.start();
        assertThat(insideFirstRun.await(5, TimeUnit.SECONDS)).isTrue();

        // The guard fires while the first run is still in flight.
        AiReindexResponse rejected = service.indexAll();
        assertThat(rejected.getIndexed()).isZero();
        assertThat(rejected.getSkipped()).isZero();
        assertThat(rejected.getDurationMs()).isZero();
        verify(vectorStore, never()).add(anyList());

        releaseFirstRun.countDown();
        first.join(TimeUnit.SECONDS.toMillis(5));

        assertThat(first.isAlive()).isFalse();
        assertThat(firstRunFailure.get()).isNull();
        assertThat(firstRunResult.get()).isNotNull();
        assertThat(firstRunResult.get().getIndexed()).isEqualTo(1);
        verify(vectorStore).add(List.of(docA));
    }

    /**
     * Regression guard for the staleness detection itself. {@code Product.translations} is a
     * {@code HashSet} and {@code BaseEntity.hashCode()} is {@code getClass().hashCode()}, so every
     * translation of a product collides into one bucket and the set iterates in whatever order the
     * rows came back in. Were the document text built in that order, the {@code contentHash} of an
     * untouched product would change between two reads and every product would be re-embedded on
     * every single run — silently, with {@code indexed > 0} and no error anywhere.
     */
    @Test
    void stalenessSurvivesUnstableTranslationSetOrder() {
        ProductDocumentFactory realFactory = new ProductDocumentFactory();
        Product readFirst = productWithTranslations(1L, "a", "ar", "en");
        Product readSecond = productWithTranslations(1L, "a", "en", "ar");

        Document firstDocument = realFactory.toDocument(readFirst);
        Document secondDocument = realFactory.toDocument(readSecond);
        assertThat(secondDocument.getMetadata())
                .containsEntry("contentHash", firstDocument.getMetadata().get("contentHash"));

        when(productRepository.findAll()).thenReturn(List.of(readFirst), List.of(readSecond));
        when(productRepository.findAllByIdInWithDetails(List.of(1L)))
                .thenReturn(List.of(readFirst), List.of(readSecond));
        when(documentFactory.toDocument(any(Product.class))).thenReturn(firstDocument, secondDocument);
        // First run: nothing stored yet. Second run: the first run's document is in the store.
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of())
                .thenReturn(List.of(firstDocument));

        assertThat(service.indexAll().getIndexed()).isEqualTo(1);
        assertThat(service.indexAll().getSkipped()).isEqualTo(1);
        verify(vectorStore).add(List.of(firstDocument));
    }

    @Test
    void upsertReindexesASingleProductWithoutScanningTheCatalog() {
        Product a = product(1L, "a");
        Document docA = document(1L, "a", "text a", "h1");

        when(productRepository.findById(1L)).thenReturn(java.util.Optional.of(a));
        when(documentFactory.toDocument(a)).thenReturn(docA);

        service.upsert(a);

        verify(vectorStore).add(List.of(docA));
        verify(productRepository, never()).findAll();
        verify(productRepository, never()).findAllByIdInWithDetails(anyList());
    }

    @Test
    void upsertSkipsAProductThatNoLongerExists() {
        Product a = product(1L, "a");
        when(productRepository.findById(1L)).thenReturn(java.util.Optional.empty());

        service.upsert(a);

        verify(vectorStore, never()).add(anyList());
        verify(documentFactory, never()).toDocument(any(Product.class));
    }

    @Test
    void upsertSkipsAnUnsavedProduct() {
        Product unsaved = product(null, "a");

        service.upsert(unsaved);

        verify(productRepository, never()).findById(anyLong());
        verify(vectorStore, never()).add(anyList());
    }

    /**
     * The one property of this class that a Mockito-based test cannot observe, so it is pinned
     * reflectively instead.
     *
     * <p>PgVectorStore writes through a {@code JdbcTemplate} on the application's {@code
     * DataSource}, which Spring's {@code JpaTransactionManager} binds to the JPA transaction; for
     * a {@code readOnly} definition it pushes that connection into {@code BEGIN READ ONLY}. A
     * {@code readOnly = true} on either writing method therefore fails every document write with
     * {@code cannot execute INSERT in a read-only transaction} — while every mocked test here
     * still passes, because the mock has no transaction and no database.
     */
    @Test
    void writingMethodsRunInAWritableTransaction() throws NoSuchMethodException {
        assertThat(transactionFor("indexAll")).isFalse();
        assertThat(transactionFor("upsert", Product.class)).isFalse();
        assertThat(transactionFor("stats")).isTrue();
    }

    private static boolean transactionFor(String method, Class<?>... parameterTypes) throws NoSuchMethodException {
        Transactional annotation = ProductIndexingService.class
                .getMethod(method, parameterTypes)
                .getAnnotation(Transactional.class);

        assertThat(annotation)
                .as("%s must stay transactional so lazy relations can be walked", method)
                .isNotNull();

        return annotation.readOnly();
    }

    @Test
    void indexAllReembedsWhenTheFreshDocumentCarriesNoHash() {
        // Mirror of the stored-side case: a fresh document with no hash must count as "not
        // indexed" rather than dereferencing null and failing the whole run.
        Product a = product(1L, "a");
        Document unhashed = new Document("product:a", "text a", Map.of("slug", "a"));

        when(productRepository.findAll()).thenReturn(List.of(a));
        when(productRepository.findAllByIdInWithDetails(List.of(1L))).thenReturn(List.of(a));
        when(documentFactory.toDocument(a)).thenReturn(unhashed);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(unhashed));

        AiReindexResponse result = service.indexAll();

        assertThat(result.getIndexed()).isEqualTo(1);
        assertThat(result.getSkipped()).isZero();
        verify(vectorStore).add(List.of(unhashed));
    }
}
