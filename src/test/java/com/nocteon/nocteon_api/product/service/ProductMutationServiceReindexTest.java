package com.nocteon.nocteon_api.product.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.nocteon.nocteon_api.common.service.HtmlSanitizer;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.nocteon.nocteon_api.ai.service.ProductIndexingService;
import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.category.repository.CategoryRepository;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.farm.repository.FarmRepository;
import com.nocteon.nocteon_api.origin.entity.Origin;
import com.nocteon.nocteon_api.origin.repository.OriginRepository;
import com.nocteon.nocteon_api.product.dto.request.ProductRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductTranslationRequest;
import com.nocteon.nocteon_api.product.dto.response.DashboardProductResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductResponse;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.product.mapper.DashboardProductMapper;
import com.nocteon.nocteon_api.product.mapper.ProductResponseMapper;
import com.nocteon.nocteon_api.product.repository.ProductMediaRepository;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.product.repository.ProductTranslationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Covers the post-commit AI index hook in {@link ProductMutationService}.
 *
 * <p>Everything here is about <em>when</em> the indexer runs. Indexing inside the
 * {@code @Transactional} body would embed an uncommitted product — and on a rollback, leave a vector
 * for something that does not exist. So the assertions are all about the gap between the
 * {@code @Transactional} method returning and the commit: before the commit the indexer must not
 * have been touched at all, and after it, exactly one hook must have fired.
 *
 * <p>Only the product id crosses into the hook. The entity handed to these methods is detached by
 * the time {@code afterCommit} runs, and {@code ProductDocumentFactory} walks lazy relations, so
 * anything read off that instance would throw {@code LazyInitializationException}. The indexer
 * re-reads the product itself.
 */
@ExtendWith(MockitoExtension.class)
class ProductMutationServiceReindexTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductTranslationRepository productTranslationRepository;

    @Mock
    private ProductMediaRepository productMediaRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private OriginRepository originRepository;

    @Mock
    private FarmRepository farmRepository;

    @Mock
    private LookupServiceHelper helper;

    @Mock
    private ProductMediaService productMediaService;

    @Mock
    private ProductResponseMapper productResponseMapper;

    @Mock
    private DashboardProductMapper dashboardProductMapper;

    @Mock
    private ProductVariantService productVariantService;

    @Mock
    private ProductCoffeeDetailsService productCoffeeDetailsService;

    @Mock
    private ProductRelationService productRelationService;

    @Mock
    private ProductIndexingService productIndexingService;

    @Mock
    private HtmlSanitizer htmlSanitizer;

    @InjectMocks
    private ProductMutationService service;

    /** Story sanitization is pass-through here; its own behaviour is covered elsewhere. */
    @BeforeEach
    void passThroughSanitizer() {
        lenient().when(htmlSanitizer.sanitize(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** Opens the synchronization context a {@code @Transactional} method would have open. */
    private static void beginTransaction() {
        TransactionSynchronizationManager.initSynchronization();
    }

    /** Fires the {@code afterCommit} half of a commit, without committing anything real. */
    private static void commit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
    }

    private static Product product(Long id, String slug) {
        return Product.builder().id(id).slug(slug).isActive(true).build();
    }

    private void stubExistingProduct(Product found) {
        when(productRepository.findBySlug("yirgacheffe")).thenReturn(Optional.of(found));
        when(dashboardProductMapper.buildDashboardResponse(found))
                .thenReturn(DashboardProductResponse.builder().build());
    }

    private static ProductTranslationRequest translation(String language, String name) {
        ProductTranslationRequest request = new ProductTranslationRequest();
        request.setLanguage(language);
        request.setName(name);
        return request;
    }

    private static ProductRequest request() {
        ProductRequest request = new ProductRequest();
        request.setCategorySlug("single-origin");
        request.setOriginSlug("ethiopia");
        request.setProductType(ProductType.COFFEE);
        request.setVariants(List.of());
        request.setMedia(List.of());
        request.setTastingNoteSlugs(List.of());
        request.setPairingSlugs(List.of());
        request.setBrewingMethods(List.of());
        return request;
    }

    // --- create -------------------------------------------------------------------------------

    @Test
    void createIndexesTheProductOnlyAfterTheCommit() {
        ProductRequest request = request();
        request.setTranslations(List.of(translation("en", "Yirgacheffe")));
        Product saved = product(99L, "yirgacheffe");

        when(categoryRepository.findBySlug("single-origin"))
                .thenReturn(Optional.of(Category.builder().build()));
        when(originRepository.findBySlug("ethiopia"))
                .thenReturn(Optional.of(Origin.builder().build()));
        when(helper.generateUniqueSlug(eq("Yirgacheffe"), any())).thenReturn("yirgacheffe");
        when(productRepository.save(any(Product.class))).thenReturn(saved);
        when(productRepository.findBySlug(eq("yirgacheffe")))
                .thenReturn(Optional.of(saved));
        when(dashboardProductMapper.buildDashboardResponse(any(Product.class)))
                .thenReturn(DashboardProductResponse.builder().build());

        beginTransaction();
        service.create(request, List.of());

        verifyNoInteractions(productIndexingService);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

        commit();

        verify(productIndexingService).upsertById(99L);
    }

    @Test
    void createPersistsTheStoryInsideTheTranslation() {
        ProductRequest request = request();
        ProductTranslationRequest en = translation("en", "Yirgacheffe");
        en.setStory("Grown at 2,000 m by the Kebele smallholders.");
        request.setTranslations(List.of(en));
        Product saved = product(99L, "yirgacheffe");

        when(categoryRepository.findBySlug("single-origin"))
                .thenReturn(Optional.of(Category.builder().build()));
        when(originRepository.findBySlug("ethiopia"))
                .thenReturn(Optional.of(Origin.builder().build()));
        when(helper.generateUniqueSlug(eq("Yirgacheffe"), any())).thenReturn("yirgacheffe");
        when(productRepository.save(any(Product.class))).thenReturn(saved);
        when(productRepository.findBySlug(eq("yirgacheffe"))).thenReturn(Optional.of(saved));
        when(dashboardProductMapper.buildDashboardResponse(any(Product.class)))
                .thenReturn(DashboardProductResponse.builder().build());

        beginTransaction();
        service.create(request, List.of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductTranslation>> captor = ArgumentCaptor.forClass(List.class);
        verify(productTranslationRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .singleElement()
                .extracting(ProductTranslation::getStory)
                .isEqualTo("Grown at 2,000 m by the Kebele smallholders.");
    }

    // --- update -------------------------------------------------------------------------------

    @Test
    void updateIndexesTheProductOnlyAfterTheCommit() {
        ProductRequest request = request();
        request.setTranslations(List.of());
        stubExistingProduct(product(7L, "yirgacheffe"));

        when(categoryRepository.findBySlug("single-origin"))
                .thenReturn(Optional.of(Category.builder().build()));
        when(originRepository.findBySlug("ethiopia"))
                .thenReturn(Optional.of(Origin.builder().build()));

        beginTransaction();
        service.update("yirgacheffe", request, List.of());

        verifyNoInteractions(productIndexingService);

        commit();

        verify(productIndexingService).upsertById(7L);
    }

    // --- toggles ------------------------------------------------------------------------------

    @Test
    void togglingActiveIndexesTheProductOnlyAfterTheCommit() {
        stubExistingProduct(product(7L, "yirgacheffe"));

        beginTransaction();
        service.toggleActive("yirgacheffe", false);

        verifyNoInteractions(productIndexingService);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

        commit();

        verify(productIndexingService).upsertById(7L);
    }

    @Test
    void togglingFeaturedIndexesTheProductOnlyAfterTheCommit() {
        stubExistingProduct(product(7L, "yirgacheffe"));

        beginTransaction();
        service.toggleFeatured("yirgacheffe", true);
        commit();

        verify(productIndexingService).upsertById(7L);
    }

    // --- delete -------------------------------------------------------------------------------

    @Test
    void deleteDropsTheVectorDocumentOnlyAfterTheCommit() {
        when(productRepository.findBySlugAndLanguage(eq("yirgacheffe"), anyString()))
                .thenReturn(Optional.of(product(7L, "yirgacheffe")));

        beginTransaction();
        service.delete("yirgacheffe");

        verifyNoInteractions(productIndexingService);

        commit();

        verify(productIndexingService).remove("yirgacheffe");
    }

    // --- the contract that keeps the mutation safe --------------------------------------------

    /**
     * The whole point of the guard: called outside a transaction — a unit test, or a future caller
     * that drops {@code @Transactional} — {@code registerSynchronization} throws
     * {@code IllegalStateException}, which would turn a working save into a 500.
     */
    @Test
    void nothingIsRegisteredWhenThereIsNoTransactionToHookOnto() {
        stubExistingProduct(product(7L, "yirgacheffe"));

        assertThatCode(() -> service.toggleActive("yirgacheffe", false)).doesNotThrowAnyException();

        verifyNoInteractions(productIndexingService);
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }

    /**
     * Ollama down must not cost the admin their product save. The reindex runs after the response
     * has already been shaped, so there is nothing to roll back — but an escaping exception would
     * still surface to the caller as a failed request.
     */
    @Test
    void aFailedUpsertAfterCommitNeverBreaksTheSave() {
        stubExistingProduct(product(7L, "yirgacheffe"));
        doThrow(new IllegalStateException("ollama down")).when(productIndexingService).upsertById(7L);

        beginTransaction();
        DashboardProductResponse response = service.toggleActive("yirgacheffe", false);

        assertThatCode(ProductMutationServiceReindexTest::commit).doesNotThrowAnyException();
        assertThat(response).isNotNull();
    }

    /** Same for the delete path: the document removal failing is not a failed product delete. */
    @Test
    void aFailedRemovalAfterCommitNeverBreaksTheDelete() {
        when(productRepository.findBySlugAndLanguage(eq("yirgacheffe"), anyString()))
                .thenReturn(Optional.of(product(7L, "yirgacheffe")));
        doThrow(new IllegalStateException("ollama down")).when(productIndexingService).remove("yirgacheffe");

        beginTransaction();
        assertThatCode(() -> service.delete("yirgacheffe")).doesNotThrowAnyException();

        assertThatCode(ProductMutationServiceReindexTest::commit).doesNotThrowAnyException();
    }

    /**
     * One save, one embedding. Two hooks for a single mutation would embed the same product twice,
     * which is exactly the cost the post-commit hook is meant to avoid.
     */
    @Test
    void aSingleSaveRegistersASingleHook() {
        stubExistingProduct(product(7L, "yirgacheffe"));

        beginTransaction();
        service.toggleActive("yirgacheffe", false);

        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
    }

    /**
     * The detached entity must never be handed to the indexer: the hook runs after the persistence
     * context has closed, and {@code ProductDocumentFactory} needs open lazy relations.
     */
    @Test
    void theHookCarriesOnlyTheIdNeverTheDetachedEntity() {
        stubExistingProduct(product(7L, "yirgacheffe"));

        beginTransaction();
        service.toggleActive("yirgacheffe", false);
        commit();

        verify(productIndexingService, org.mockito.Mockito.never()).upsert(any(Product.class));
        verify(productIndexingService).upsertById(7L);
    }

    /** Unrelated mutations must not drag the vector store into the picture. */
    @Test
    void mediaChangesDoNotReindexAnything() {
        when(productRepository.findBySlug(eq("yirgacheffe")))
                .thenReturn(Optional.of(product(7L, "yirgacheffe")));

        beginTransaction();
        service.uploadMedia("yirgacheffe", List.of(), List.of());
        commit();

        verifyNoInteractions(productIndexingService);
    }
}