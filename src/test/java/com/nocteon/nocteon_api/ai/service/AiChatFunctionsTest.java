package com.nocteon.nocteon_api.ai.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;

import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethod;
import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethodTranslation;
import com.nocteon.nocteon_api.brewingMethod.repository.BrewingMethodRepository;
import com.nocteon.nocteon_api.coffeeVariety.entity.CoffeeVariety;
import com.nocteon.nocteon_api.coffeeVariety.entity.CoffeeVarietyTranslation;
import com.nocteon.nocteon_api.farm.entity.Farm;
import com.nocteon.nocteon_api.farm.entity.FarmTranslation;
import com.nocteon.nocteon_api.origin.entity.Origin;
import com.nocteon.nocteon_api.origin.entity.OriginTranslation;
import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethod;
import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethodTranslation;
import com.nocteon.nocteon_api.product.dto.response.ProductCardResponse;
import com.nocteon.nocteon_api.product.entity.CoffeeDetails;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.product.mapper.ProductResponseMapper;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevel;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevelTranslation;
import com.nocteon.nocteon_api.tastingNote.entity.TastingNote;
import com.nocteon.nocteon_api.tastingNote.entity.TastingNoteTranslation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A tool that throws takes the whole turn down with it: Spring AI has no way to hand a model a
 * failed call, so the exception unwinds past the advisor chain and out of the endpoint. Every test
 * here therefore asserts an <em>empty or short value</em> rather than a raised exception, because
 * that is the only contract a tool can honour.
 *
 * <p>The fact strings are asserted against rather than composed from the model's own vocabulary,
 * so a passing test means every number in the string reached the model from the database.
 */
@ExtendWith(MockitoExtension.class)
class AiChatFunctionsTest {

    private static final String EN = "en";

    @Mock private AiSearchService aiSearchService;
    @Mock private ProductRepository productRepository;
    @Mock private ProductResponseMapper productResponseMapper;
    @Mock private BrewingMethodRepository brewingMethodRepository;
    @Mock private TransactionOperations transactions;

    private AiChatFunctions functions;

    @BeforeEach
    void setUp() {
        // The real transactional boundary, stubbed to run the work inline: the point of these tests
        // is what the tools read and return, not how the read is wrapped.
        lenient().when(transactions.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback =
                    invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        functions = new AiChatFunctions(
                aiSearchService,
                productRepository,
                productResponseMapper,
                brewingMethodRepository,
                transactions);
    }

    /** Similarities in the order the vector store returned them, which is not sorted. */
    private static Map<Long, Double> candidates(double... scoreById) {
        Map<Long, Double> candidates = new LinkedHashMap<>();
        for (int i = 0; i < scoreById.length; i++) {
            candidates.put((long) (i + 1), scoreById[i]);
        }
        return candidates;
    }

    // --- search_products ---------------------------------------------------------------------

    @Test
    void searchReturnsNothingWhenSemanticSearchIsOff() {
        when(aiSearchService.semanticCandidates(any(), any(Integer.class))).thenReturn(Map.of());

        assertThat(functions.searchProducts("fruity light roast", 3)).isEmpty();
    }

    @Test
    void searchNeverThrowsWhenTheEmbeddingModelIsUnreachable() {
        when(aiSearchService.semanticCandidates(any(), any(Integer.class)))
                .thenThrow(new RuntimeException("ollama down"));

        assertThat(functions.searchProducts("anything", 3)).isEmpty();
    }

    @Test
    void searchNeverThrowsWhenTheCatalogReadFails() {
        when(aiSearchService.semanticCandidates(any(), any(Integer.class)))
                .thenReturn(Map.of(1L, 0.9));
        when(productRepository.findAllByIdInWithDetails(anyList()))
                .thenThrow(new RuntimeException("connection reset"));

        assertThat(functions.searchProducts("anything", 3)).isEmpty();
    }

    @Test
    void searchNeverThrowsForABlankQuery() {
        assertThat(functions.searchProducts("  ", 3)).isEmpty();

        verify(aiSearchService, never()).semanticCandidates(any(), any(Integer.class));
    }

    @Test
    void searchAsksForAtMostEightCandidates() {
        when(aiSearchService.semanticCandidates(any(), any(Integer.class))).thenReturn(Map.of());

        functions.searchProducts("anything", 50);

        verify(aiSearchService).semanticCandidates("anything", AiChatFunctions.MAX_RESULTS);
    }

    @Test
    void searchNeverAsksForZeroOrFewerCandidates() {
        when(aiSearchService.semanticCandidates(any(), any(Integer.class))).thenReturn(Map.of());

        functions.searchProducts("anything", 0);

        verify(aiSearchService).semanticCandidates("anything", 1);
    }

    /**
     * The score is a similarity — higher is better — and it is consumed as returned. Inverting it
     * here would promote the least similar coffee in the catalog, and a shopper would be the one
     * to notice.
     */
    @Test
    void searchOrdersCandidatesByDescendingSimilarity() {
        when(aiSearchService.semanticCandidates("fruity", 3)).thenReturn(candidates(0.10, 0.95, 0.42));
        when(productRepository.findAllByIdInWithDetails(anyList())).thenAnswer(call -> List.of(
                product(1L, "weak"),
                product(2L, "strong"),
                product(3L, "middle")));

        assertThat(functions.searchProducts("fruity", 3))
                .containsExactly("strong", "middle", "weak");
    }

    @Test
    void searchReturnsNoSlugsForAProductIdTheCatalogNoLongerHolds() {
        when(aiSearchService.semanticCandidates("fruity", 3)).thenReturn(Map.of(99L, 0.9));
        when(productRepository.findAllByIdInWithDetails(anyList())).thenReturn(List.of());

        assertThat(functions.searchProducts("fruity", 3)).isEmpty();
    }

    /** An indexed row can outlive the product being on sale; offering it is an unusable suggestion. */
    @Test
    void searchSkipsInactiveProducts() {
        when(aiSearchService.semanticCandidates("fruity", 3)).thenReturn(candidates(0.9, 0.5));
        Product active = product(1L, "active");
        Product inactive = product(2L, "gone");
        inactive.setActive(false);
        when(productRepository.findAllByIdInWithDetails(anyList()))
                .thenReturn(List.of(active, inactive));

        assertThat(functions.searchProducts("fruity", 3)).containsExactly("active");
    }

    @Test
    void searchRemembersWhatItReturned() {
        when(aiSearchService.semanticCandidates("fruity", 3)).thenReturn(candidates(0.9));
        when(productRepository.findAllByIdInWithDetails(anyList()))
                .thenReturn(List.of(product(1L, "yirgacheffe")));

        functions.searchProducts("fruity", 3);

        assertThat(functions.citedSlugs()).containsExactly("yirgacheffe");
    }

    /** Two turns must not share a collector, or concurrent visitors would swap product rails. */
    @Test
    void eachTurnKeepsItsOwnCitations() {
        when(aiSearchService.semanticCandidates("fruity", 3)).thenReturn(candidates(0.9));
        when(productRepository.findAllByIdInWithDetails(anyList()))
                .thenReturn(List.of(product(1L, "yirgacheffe")));

        functions.searchProducts("fruity", 3);
        AiChatFunctions otherTurn = functions.newTurn();

        assertThat(otherTurn.citedSlugs()).isEmpty();
        assertThat(functions.citedSlugs()).containsExactly("yirgacheffe");
    }

    // --- get_product_details -------------------------------------------------------------------

    @Test
    void detailsReportOnlyDatabaseFacts() {
        when(productRepository.findBySlug("yirgacheffe")).thenReturn(Optional.of(fullCoffee()));

        assertThat(functions.getProductDetails("yirgacheffe"))
                .contains("Yirgacheffe")
                .contains("yirgacheffe")
                .contains("Ethiopia")
                .contains("Konga")
                .contains("Light")
                .contains("Washed")
                .contains("Heirloom")
                .contains("Bergamot")
                .contains("120")
                .contains("180")
                .contains("in stock");
    }

    /**
     * The price is the value a hallucinating model would most plausibly invent, so the arithmetic
     * is pinned: only active variants are quoted, and an inactive variant's price is never
     * mentioned even when it is the highest.
     */
    @Test
    void detailsQuoteOnlyActiveVariantPrices() {
        Product product = fullCoffee();
        product.getVariants().add(ProductVariant.builder()
                .id(30L)
                .price(new BigDecimal("999"))
                .isActive(false)
                .build());

        when(productRepository.findBySlug("yirgacheffe")).thenReturn(Optional.of(product));

        assertThat(functions.getProductDetails("yirgacheffe"))
                .contains("120")
                .contains("180")
                .doesNotContain("999");
    }

    @Test
    void detailsReportAnUnknownPriceRatherThanARetiredOne() {
        Product product = fullCoffee();
        product.getVariants().forEach(variant -> variant.setActive(false));

        when(productRepository.findBySlug("yirgacheffe")).thenReturn(Optional.of(product));

        String details = functions.getProductDetails("yirgacheffe");

        assertThat(details).contains("Price: unknown").doesNotContain("120").doesNotContain("180");
        assertThat(details.toLowerCase(Locale.ROOT)).contains("unavailable");
    }

    @Test
    void detailsOfAnUnknownSlugAreShortAndDoNotThrow() {
        when(productRepository.findBySlug("nope")).thenReturn(Optional.empty());

        assertThat(functions.getProductDetails("nope")).isNotBlank();
    }

    @Test
    void detailsOfADelistedCoffeeCarryNoFactsAtAll() {
        Product product = fullCoffee();
        product.setActive(false);

        when(productRepository.findBySlug("yirgacheffe")).thenReturn(Optional.of(product));

        assertThat(functions.getProductDetails("yirgacheffe"))
                .doesNotContain("Ethiopia")
                .doesNotContain("120");
    }

    @Test
    void detailsNeverThrowWhenTheCatalogReadFails() {
        when(productRepository.findBySlug(any())).thenThrow(new RuntimeException("db down"));

        assertThat(functions.getProductDetails("yirgacheffe")).isNotBlank();
    }

    @Test
    void detailsOfANullSlugDoNotThrow() {
        assertThat(functions.getProductDetails(null)).isNotBlank();

        verify(productRepository, never()).findBySlug(any());
    }

    // --- get_brewing_guide -----------------------------------------------------------------------

    @Test
    void brewingGuideReturnsTheStoredDescription() {
        when(brewingMethodRepository.findBySlugWithTranslations("v60"))
                .thenReturn(Optional.of(brewingMethod("v60", "Pour-over", "Brew slowly in a cone.")));

        assertThat(functions.getBrewingGuide("v60")).contains("Brew slowly in a cone.");
    }

    /** The slug is what the model has, so the lookup has to be tolerant of its casing. */
    @Test
    void brewingGuideNormalisesTheMethodSlug() {
        when(brewingMethodRepository.findBySlugWithTranslations("french-press"))
                .thenReturn(Optional.of(brewingMethod("french-press", "French press", "Steep four minutes.")));

        assertThat(functions.getBrewingGuide("French-Press")).contains("Steep four minutes.");
    }

    @Test
    void brewingGuideOfAnUnknownMethodIsShortAndDoesNotThrow() {
        when(brewingMethodRepository.findBySlugWithTranslations("aeropress-pro"))
                .thenReturn(Optional.empty());

        assertThat(functions.getBrewingGuide("aeropress-pro")).isNotBlank();
    }

    @Test
    void brewingGuideNeverThrowsWhenTheCatalogReadFails() {
        when(brewingMethodRepository.findBySlugWithTranslations(any()))
                .thenThrow(new RuntimeException("db down"));

        assertThat(functions.getBrewingGuide("v60")).isNotBlank();
    }

    @Test
    void brewingGuideOfANullMethodDoesNotThrow() {
        assertThat(functions.getBrewingGuide(null)).isNotBlank();
    }

    // --- card resolution ----------------------------------------------------------------------

    @Test
    void cardsResolveInTheOrderTheToolsReturnedThem() {
        Product first = product(1L, "strong");
        Product second = product(2L, "middle");
        when(productRepository.findBySlugIn(List.of("strong", "middle")))
                .thenReturn(List.of(second, first));
        when(productResponseMapper.buildListResponse(first, EN))
                .thenReturn(ProductCardResponse.builder().slug("strong").build());
        when(productResponseMapper.buildListResponse(second, EN))
                .thenReturn(ProductCardResponse.builder().slug("middle").build());

        assertThat(functions.cardsFor(List.of("strong", "middle"), EN))
                .extracting(ProductCardResponse::getSlug)
                .containsExactly("strong", "middle");
    }

    @Test
    void cardsDropASlugTheCatalogNoLongerHolds() {
        Product held = product(1L, "strong");
        when(productRepository.findBySlugIn(List.of("strong", "gone")))
                .thenReturn(List.of(held));
        when(productResponseMapper.buildListResponse(held, EN))
                .thenReturn(ProductCardResponse.builder().slug("strong").build());

        assertThat(functions.cardsFor(List.of("strong", "gone"), EN))
                .extracting(ProductCardResponse::getSlug)
                .containsExactly("strong");
    }

    @Test
    void cardsAreEmptyWhenTheToolsFoundNothing() {
        assertThat(functions.cardsFor(List.of(), EN)).isEmpty();

        verify(productRepository, never()).findBySlugIn(anyList());
    }

    @Test
    void cardsAreEmptyForANullSlugList() {
        assertThat(functions.cardsFor(null, EN)).isEmpty();
    }

    @Test
    void cardResolutionNeverThrowsWhenTheCatalogReadFails() {
        when(productRepository.findBySlugIn(anyList()))
                .thenThrow(new RuntimeException("db down"));

        assertThat(functions.cardsFor(List.of("strong"), EN)).isEmpty();
    }

    @Test
    void cardResolutionSkipsASlugTheMapperCannotProject() {
        Product product = product(1L, "strong");
        when(productRepository.findBySlugIn(List.of("strong"))).thenReturn(List.of(product));
        when(productResponseMapper.buildListResponse(any(), any())).thenReturn(null);

        assertThat(functions.cardsFor(List.of("strong"), EN)).isEmpty();
    }

    // --- fixtures ---------------------------------------------------------------------------------

    private static Product fullCoffee() {
        Product product = product(1L, "yirgacheffe");
        product.getTranslations().add(ProductTranslation.builder()
                .language(EN)
                .name("Yirgacheffe")
                .build());

        product.setOrigin(Origin.builder().id(1L).slug("ethiopia")
                .translations(List.of(OriginTranslation.builder().language(EN).name("Ethiopia").build()))
                .build());
        product.setFarm(Farm.builder().id(1L).slug("konga")
                .translations(List.of(FarmTranslation.builder().language(EN).name("Konga").build()))
                .build());

        product.setCoffeeDetails(CoffeeDetails.builder()
                .id(1L)
                .roastLevel(RoastLevel.builder().id(1L).slug("light-roast")
                        .translations(List.of(
                                RoastLevelTranslation.builder().language(EN).name("Light").build()))
                        .build())
                .processingMethod(ProcessingMethod.builder().id(1L).slug("washed")
                        .translations(List.of(
                                ProcessingMethodTranslation.builder().language(EN).name("Washed").build()))
                        .build())
                .coffeeVariety(CoffeeVariety.builder().id(1L).slug("heirloom")
                        .translations(List.of(
                                CoffeeVarietyTranslation.builder().language(EN).name("Heirloom").build()))
                        .build())
                .build());

        product.getTastingNotes().add(TastingNote.builder().slug("bergamot")
                .translations(List.of(
                        TastingNoteTranslation.builder().language(EN).name("Bergamot").build()))
                .build());

        product.getVariants().add(ProductVariant.builder()
                .id(10L).price(new BigDecimal("120")).isActive(true).stockQuantity(5).build());
        product.getVariants().add(ProductVariant.builder()
                .id(20L).price(new BigDecimal("180")).isActive(true).stockQuantity(2).build());

        return product;
    }

    private static Product product(Long id, String slug) {
        return Product.builder()
                .id(id)
                .slug(slug)
                .productType(ProductType.COFFEE)
                .build();
    }

    private static BrewingMethod brewingMethod(String slug, String name, String description) {
        return BrewingMethod.builder()
                .id(1L)
                .slug(slug)
                .translations(List.of(BrewingMethodTranslation.builder()
                        .language(EN)
                        .name(name)
                        .description(description)
                        .build()))
                .build();
    }
}