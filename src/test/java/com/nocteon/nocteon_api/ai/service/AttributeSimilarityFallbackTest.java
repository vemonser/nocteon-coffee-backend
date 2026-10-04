package com.nocteon.nocteon_api.ai.service;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.origin.entity.Origin;
import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethod;
import com.nocteon.nocteon_api.product.entity.CoffeeDetails;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevel;
import com.nocteon.nocteon_api.tastingNote.entity.TastingNote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The attribute fallback is the safety net under the vector path, so it is tested with no
 * Spring context, no mocks and no collaborators at all — the class has none. If any of these
 * needed a mock to run, the net would not be independent of the AI path and would go untested
 * precisely when the AI path is the thing that is broken.
 */
@ExtendWith(MockitoExtension.class)
class AttributeSimilarityFallbackTest {

    private final AttributeSimilarityFallback fallback = new AttributeSimilarityFallback();

    private static Origin origin(String slug) {
        return slug == null ? null : Origin.builder().id(1L).slug(slug).build();
    }

    private static Category category(String slug) {
        return slug == null ? null : Category.builder().id(1L).slug(slug).build();
    }

    private static Product product(Long id, String slug, String originSlug, String categorySlug) {
        return Product.builder()
                .id(id)
                .slug(slug)
                .productType(ProductType.COFFEE)
                .origin(origin(originSlug))
                .category(category(categorySlug))
                .build();
    }

    /** The same product, plus the coffee-only relations the weights also read. */
    private static Product coffee(Long id, String slug, String originSlug, String categorySlug,
            String roastSlug, String processSlug, List<String> noteSlugs) {

        return Product.builder()
                .id(id)
                .slug(slug)
                .productType(ProductType.COFFEE)
                .origin(origin(originSlug))
                .category(category(categorySlug))
                .coffeeDetails(CoffeeDetails.builder()
                        .id(id)
                        .roastLevel(roastSlug == null ? null : RoastLevel.builder().id(2L).slug(roastSlug).build())
                        .processingMethod(processSlug == null ? null
                                : ProcessingMethod.builder().id(3L).slug(processSlug).build())
                        .build())
                .tastingNotes(noteSlugs.stream()
                        .map(s -> (TastingNote) TastingNote.builder().slug(s).build())
                        .toList())
                .build();
    }

    // --- ranking ----------------------------------------------------------------------

    @Test
    void sameOriginScoresHigherThanDifferentOrigin() {
        Product source = product(1L, "src", "ethiopia", "coffee");
        Product same = product(2L, "same", "ethiopia", "coffee");
        Product diff = product(3L, "diff", "colombia", "coffee");

        Map<Long, Double> scores = fallback.rank(source, List.of(same, diff));

        assertThat(scores.get(2L)).isGreaterThan(scores.get(3L));
    }

    @Test
    void nullOriginOnEitherSideDoesNotThrow() {
        Product source = product(1L, "src", null, "coffee");
        Product other = product(2L, "other", "ethiopia", "coffee");

        assertThat(fallback.rank(source, List.of(other))).containsKey(2L);
    }

    @Test
    void sourceItselfIsExcludedFromResults() {
        Product source = product(1L, "src", "ethiopia", "coffee");
        Product other = product(2L, "other", "ethiopia", "coffee");

        assertThat(fallback.rank(source, List.of(source, other))).doesNotContainKey(1L);
    }

    @Test
    void moreSharedAttributesScoreHigher() {
        Product source = coffee(1L, "src", "ethiopia", "coffee", "light-roast", "washed", List.of("berry"));
        Product close = coffee(2L, "close", "ethiopia", "coffee", "light-roast", "washed", List.of("berry"));
        Product far = product(3L, "far", "colombia", "merch");

        Map<Long, Double> scores = fallback.rank(source, List.of(close, far));

        assertThat(scores.get(2L)).isGreaterThan(scores.get(3L));
    }

    /**
     * Two {@code Origin} instances for the same origin are different objects — JPA hands out a
     * fresh proxy per persistence context — so identity comparison would score every candidate
     * at zero. Matching on the slug is what makes the net work at all.
     */
    @Test
    void comparesBySlugRatherThanEntityIdentity() {
        Product source = product(1L, "src", "ethiopia", "coffee");
        Product other = Product.builder()
                .id(2L)
                .slug("other")
                .productType(ProductType.COFFEE)
                .origin(Origin.builder().id(999L).slug("ethiopia").build())
                .category(Category.builder().id(888L).slug("coffee").build())
                .build();

        assertThat(fallback.rank(source, List.of(other)).get(2L)).isGreaterThan(0.0);
    }

    /**
     * Two absent values comparing equal would make every unrelated product match on every
     * relation, so a blank slug on either side is never a match.
     */
    @Test
    void blankSlugsOnEitherSideNeverMatch() {
        Product source = product(1L, "src", "  ", "coffee");
        Product other = product(2L, "other", "", "coffee");

        assertThat(fallback.rank(source, List.of(other)).get(2L)).isEqualTo(0.1);
    }

    @Test
    void nullCategoryOnEitherSideDoesNotThrow() {
        Product source = product(1L, "src", "ethiopia", null);
        Product other = product(2L, "other", "ethiopia", null);

        assertThat(fallback.rank(source, List.of(other))).containsKey(2L);
    }

    /**
     * {@code coffeeDetails} is {@code null} for every non-coffee product type, so a merchandise
     * candidate must not blow up the whole ranking.
     */
    @Test
    void nullCoffeeDetailsOnEitherSideDoesNotThrow() {
        Product source = coffee(1L, "src", "ethiopia", "coffee", "light-roast", "washed", List.of("berry"));
        Product merch = product(3L, "merch", null, "merch");

        assertThatCode(() -> fallback.rank(source, List.of(merch))).doesNotThrowAnyException();
        assertThat(fallback.rank(source, List.of(merch))).containsKey(3L);
    }

    @Test
    void nullTastingNoteListOnEitherSideDoesNotThrow() {
        Product source = Product.builder()
                .id(1L).slug("src").productType(ProductType.COFFEE)
                .category(category("coffee"))
                .tastingNotes(null)
                .build();
        Product other = coffee(2L, "other", "ethiopia", "coffee", "light-roast", "washed", List.of("berry"));

        assertThatCode(() -> fallback.rank(source, List.of(other))).doesNotThrowAnyException();
    }

    /**
     * Each shared note is worth {@code 0.15}, but the notes as a group stop at {@code 0.3}. Five
     * shared notes would be 0.75 uncapped — enough to outrank a shared origin outright, which is
     * backwards: note overlap is the least discriminating signal of the five, since nearly
     * every specialty coffee has something berry in it.
     */
    @Test
    void capsTheTastingNoteContribution() {
        Product source = coffee(1L, "src", null, null, null, null, List.of("a", "b", "c", "d", "e"));
        Product manyNotes = coffee(2L, "many", null, null, null, null, List.of("a", "b", "c", "d", "e"));

        assertThat(fallback.rank(source, List.of(manyNotes)).get(2L)).isEqualTo(0.3);
    }

    /** Below the cap the contribution is linear: one note 0.15, two notes 0.30. */
    @Test
    void scoresEachSharedNoteLinearlyBelowTheCap() {
        Product source = coffee(1L, "src", null, null, null, null, List.of("a", "b"));
        Product oneNote = coffee(2L, "one", null, null, null, null, List.of("a", "z"));
        Product twoNotes = coffee(3L, "two", null, null, null, null, List.of("a", "b", "z"));

        Map<Long, Double> scores = fallback.rank(source, List.of(oneNote, twoNotes));

        assertThat(scores.get(2L)).isEqualTo(0.15);
        assertThat(scores.get(3L)).isEqualTo(0.3);
    }

    @Test
    void keepsCandidateOrderAndRanksDescendingScores() {
        Product source = product(1L, "src", "ethiopia", "coffee");
        Product weak = product(2L, "weak", "colombia", "coffee");
        Product strong = product(3L, "strong", "ethiopia", "coffee");

        Map<Long, Double> scores = fallback.rank(source, List.of(weak, strong));

        assertThat(scores).containsExactly(Map.entry(2L, 0.1), Map.entry(3L, 0.5));
    }

    @Test
    void anEmptyCandidateListRanksNothing() {
        assertThat(fallback.rank(product(1L, "src", "ethiopia", "coffee"), List.of())).isEmpty();
    }

    // --- reason -----------------------------------------------------------------------

    @Test
    void reasonMentionsOriginWhenShared() {
        Product source = product(1L, "src", "ethiopia", "coffee");
        Product same = product(2L, "same", "ethiopia", "coffee");

        String reason = fallback.reason(source, same, 0.4);

        assertThat(reason).containsIgnoringCase("origin");
    }

    @Test
    void reasonIsNeverBlankEvenWithNoSharedAttributes() {
        Product source = product(1L, "src", "ethiopia", "coffee");
        Product diff = product(3L, "diff", "colombia", "merch");

        assertThat(fallback.reason(source, diff, 0.1)).isNotBlank();
    }

    @Test
    void reasonListsEveryMatchedAttributeSeparatedByADot() {
        Product source = coffee(1L, "src", "ethiopia", "coffee", "light-roast", "washed", List.of("berry"));
        Product same = coffee(2L, "same", "ethiopia", "coffee", "light-roast", "washed", List.of("berry"));

        String reason = fallback.reason(source, same, 1.0);

        assertThat(reason)
                .contains("Origin")
                .contains("Roast")
                .contains("Process")
                .contains("Category")
                .contains(" · ");
    }

    @Test
    void reasonDoesNotMentionUnmatchedAttributes() {
        Product source = coffee(1L, "src", "ethiopia", "coffee", "light-roast", "washed", List.of("berry"));
        Product other = product(2L, "other", "colombia", "merch");

        String reason = fallback.reason(source, other, 0.0);

        assertThat(reason)
                .doesNotContain("Origin")
                .doesNotContain("Roast")
                .doesNotContain("Process");
    }

    @Test
    void reasonIsNeverBlankForEveryPossibleWeightCombination() {
        Product source = coffee(1L, "src", "ethiopia", "coffee", "light-roast", "washed", List.of("berry"));
        Product bare = Product.builder().id(9L).slug("bare").productType(ProductType.EQUIPMENT).build();

        for (double score : new double[] { 0.0, 0.1, 0.4, 0.9, 1.0, -1.0 }) {
            assertThat(fallback.reason(source, bare, score)).isNotBlank();
            assertThat(fallback.reason(bare, bare, score)).isNotBlank();
            assertThat(fallback.reason(source, source, score)).isNotBlank();
        }
    }
}