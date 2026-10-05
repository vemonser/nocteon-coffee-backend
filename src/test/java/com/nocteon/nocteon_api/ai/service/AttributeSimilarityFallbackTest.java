package com.nocteon.nocteon_api.ai.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;

import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.category.entity.CategoryTranslation;
import com.nocteon.nocteon_api.origin.entity.Origin;
import com.nocteon.nocteon_api.origin.entity.OriginTranslation;
import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethod;
import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethodTranslation;
import com.nocteon.nocteon_api.product.entity.CoffeeDetails;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevel;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevelTranslation;
import com.nocteon.nocteon_api.tastingNote.entity.TastingNote;
import com.nocteon.nocteon_api.tastingNote.entity.TastingNoteTranslation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The attribute fallback is the safety net under the vector path, so it is tested with no Spring
 * context and no mocks — the only collaborator it has is a {@code MessageSource}, and that one
 * is the <em>real</em> {@link ResourceBundleMessageSource} over the production
 * {@code messages} / {@code messages_ar} bundles rather than a stub. If a key were dropped from
 * the Arabic bundle, these tests fail; a mocked message source would have hidden that.
 *
 * <p>That is the other half of why the tests use no mocks: they still pass when the entire AI
 * path is broken, which is exactly the moment they matter.
 */
@ExtendWith(MockitoExtension.class)
class AttributeSimilarityFallbackTest {

    private static final String EN = "en";
    private static final String AR = "ar";

    private final AttributeSimilarityFallback fallback = new AttributeSimilarityFallback(messageSource());

    /**
     * The production wiring from {@code LocaleConfig}, verbatim.
     *
     * <p>{@code UTF-8} is not optional: the Arabic bundle is UTF-8 and the JVM platform default
     * would otherwise mangle every Arabic character in these assertions.
     */
    private static ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("messages");
        source.setDefaultEncoding("UTF-8");
        source.setDefaultLocale(Locale.ENGLISH);
        return source;
    }

    /**
     * Arabic display names for every slug used below.
     *
     * <p>An explicit table rather than a derived string on purpose: an Arabic name built by
     * gluing a prefix onto the slug would itself contain the slug, and the test that proves the
     * reason shows the translated name rather than the slug could then never fail.
     */
    private static final Map<String, String> ARABIC_NAMES = Map.ofEntries(
            Map.entry("ethiopia", "إثيوبيا"),
            Map.entry("colombia", "كولومبيا"),
            Map.entry("coffee", "قهوة"),
            Map.entry("merch", "منتجات"),
            Map.entry("light-roast", "تحميص خفيف"),
            Map.entry("washed", "مغسول"),
            Map.entry("berry", "توت"),
            Map.entry("zulu", "زيلو"),
            Map.entry("extra", "إضافي"),
            Map.entry("a", "ألف"),
            Map.entry("b", "باء"),
            Map.entry("c", "جيم"),
            Map.entry("d", "دال"),
            Map.entry("e", "هاء"));

    private static String arabicName(String slug) {
        return ARABIC_NAMES.getOrDefault(slug, "غير معروف");
    }

    private static Origin origin(String slug) {
        return slug == null ? null : Origin.builder().id(1L).slug(slug)
                .translations(List.of(
                        OriginTranslation.builder().language(EN).name(capitalize(slug)).build(),
                        OriginTranslation.builder().language(AR).name(arabicName(slug)).build()))
                .build();
    }

    private static Category category(String slug) {
        return slug == null ? null : Category.builder().id(1L).slug(slug)
                .translations(List.of(
                        CategoryTranslation.builder().language(EN).name(capitalize(slug)).build(),
                        CategoryTranslation.builder().language(AR).name(arabicName(slug)).build()))
                .build();
    }

    private static RoastLevel roast(String slug) {
        return slug == null ? null : RoastLevel.builder().id(2L).slug(slug)
                .translations(List.of(
                        RoastLevelTranslation.builder().language(EN).name(capitalize(slug)).build(),
                        RoastLevelTranslation.builder().language(AR).name(arabicName(slug)).build()))
                .build();
    }

    private static ProcessingMethod process(String slug) {
        return slug == null ? null : ProcessingMethod.builder().id(3L).slug(slug)
                .translations(List.of(
                        ProcessingMethodTranslation.builder().language(EN).name(capitalize(slug)).build(),
                        ProcessingMethodTranslation.builder().language(AR).name(arabicName(slug)).build()))
                .build();
    }

    private static TastingNote note(String slug) {
        return TastingNote.builder().slug(slug)
                .translations(List.of(
                        TastingNoteTranslation.builder().language(EN).name(capitalize(slug)).build(),
                        TastingNoteTranslation.builder().language(AR).name(arabicName(slug)).build()))
                .build();
    }

    private static String capitalize(String slug) {
        return slug.isEmpty() ? slug : Character.toUpperCase(slug.charAt(0)) + slug.substring(1);
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
                        .roastLevel(roast(roastSlug))
                        .processingMethod(process(processSlug))
                        .build())
                .tastingNotes(noteSlugs.stream()
                        .map(AttributeSimilarityFallbackTest::note)
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
     * Ranking still compares slugs, never display names or entity identity: JPA hands out a
     * fresh proxy per persistence context, so identity comparison would score every candidate
     * at zero. The localized names added for the reason text must not leak into the weights.
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

    @Test
    void productsSharingOnlyADisplayNameAreNotTreatedAsSimilar() {
        // Two origins with different slugs but an identical Arabic name: translatable text is
        // for humans, never an identity.
        Product source = Product.builder()
                .id(1L).slug("src").productType(ProductType.COFFEE)
                .origin(Origin.builder().id(1L).slug("ethiopia")
                        .translations(List.of(OriginTranslation.builder().language(AR).name("مشترك").build()))
                        .build())
                .category(Category.builder().id(2L).slug("coffee").build())
                .build();
        Product other = Product.builder()
                .id(2L).slug("other").productType(ProductType.COFFEE)
                .origin(Origin.builder().id(2L).slug("colombia")
                        .translations(List.of(OriginTranslation.builder().language(AR).name("مشترك").build()))
                        .build())
                .category(Category.builder().id(3L).slug("coffee").build())
                .build();

        assertThat(fallback.rank(source, List.of(other)).get(2L)).isEqualTo(0.1);
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
     * backwards: note overlap is the least discriminating signal of the five, since nearly every
     * specialty coffee has something berry in it.
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

    // --- reason: English ---------------------------------------------------------------

    @Test
    void reasonMentionsOriginWhenShared() {
        Product source = product(1L, "src", "ethiopia", "coffee");
        Product same = product(2L, "same", "ethiopia", "coffee");

        String reason = fallback.reason(source, same, 0.4, EN);

        assertThat(reason).containsIgnoringCase("origin");
    }

    @Test
    void reasonIsNeverBlankEvenWithNoSharedAttributes() {
        Product source = product(1L, "src", "ethiopia", "coffee");
        Product diff = product(3L, "diff", "colombia", "merch");

        assertThat(fallback.reason(source, diff, 0.1, EN)).isNotBlank();
    }

    @Test
    void reasonListsEveryMatchedAttributeSeparatedByADot() {
        Product source = coffee(1L, "src", "ethiopia", "coffee", "light-roast", "washed", List.of("berry"));
        Product same = coffee(2L, "same", "ethiopia", "coffee", "light-roast", "washed", List.of("berry"));

        String reason = fallback.reason(source, same, 1.0, EN);

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

        String reason = fallback.reason(source, other, 0.0, EN);

        assertThat(reason)
                .doesNotContain("Origin")
                .doesNotContain("Roast")
                .doesNotContain("Process");
    }

    @Test
    void reasonIsNeverBlankForEveryPossibleWeightCombination() {
        Product source = coffee(1L, "src", "ethiopia", "coffee", "light-roast", "washed", List.of("berry"));
        Product bare = Product.builder().id(9L).slug("bare").productType(ProductType.EQUIPMENT).build();

        for (String language : List.of(EN, AR)) {
            for (double score : new double[] { 0.0, 0.1, 0.4, 0.9, 1.0, -1.0 }) {
                assertThat(fallback.reason(source, bare, score, language)).isNotBlank();
                assertThat(fallback.reason(bare, bare, score, language)).isNotBlank();
                assertThat(fallback.reason(source, source, score, language)).isNotBlank();
            }
        }
    }

    // --- reason: localized labels and display names -------------------------------------

    /**
     * The point of the fix: an Arabic request must not get an English chip. Asserted against the
     * real Arabic bundle, so deleting the key breaks the build rather than shipping an English
     * string on the Arabic storefront.
     */
    @Test
    void arabicLocaleProducesAnArabicReason() {
        Product source = product(1L, "src", "ethiopia", "coffee");
        Product same = product(2L, "same", "ethiopia", "coffee");

        String reason = fallback.reason(source, same, 0.4, AR);

        assertThat(reason)
                .contains("الأصل")
                .doesNotContainIgnoringCase("origin");
    }

    @Test
    void arabicReasonUsesTheTranslatedDisplayNameNotTheSlug() {
        Product source = coffee(1L, "src", "ethiopia", "coffee", "light-roast", "washed", List.of("berry"));
        Product same = coffee(2L, "same", "ethiopia", "coffee", "light-roast", "washed", List.of("berry"));

        String arabic = fallback.reason(source, same, 1.0, AR);

        assertThat(arabic)
                .contains("إثيوبيا", "تحميص خفيف", "مغسول", "توت")
                .doesNotContain("ethiopia", "light-roast", "washed", "berry");
    }

    @Test
    void englishReasonUsesTheEnglishDisplayNameNotTheSlugNorTheArabicOne() {
        Product source = coffee(1L, "src", "ethiopia", "coffee", "light-roast", "washed", List.of("berry"));
        Product same = coffee(2L, "same", "ethiopia", "coffee", "light-roast", "washed", List.of("berry"));

        String english = fallback.reason(source, same, 1.0, EN);

        assertThat(english)
                .contains("Ethiopia", "Light-roast", "Washed", "Berry")
                .doesNotContain("إثيوبيا", "تحميص خفيف", "مغسول", "توت");
    }

    @Test
    void theSameProductProducesADifferentReasonPerLocale() {
        Product source = product(1L, "src", "ethiopia", "coffee");
        Product same = product(2L, "same", "ethiopia", "coffee");

        assertThat(fallback.reason(source, same, 0.4, EN))
                .isNotEqualTo(fallback.reason(source, same, 0.4, AR));
    }

    /**
     * The resolution chain is matching language, then any translation, then the slug. A relation
     * with no row for the requested language still renders a real name rather than a slug, which
     * matches how {@code GlobalSearchService} resolves the same entities.
     */
    @Test
    void fallsBackToAnAvailableTranslationWhenTheRequestedLocaleHasNone() {
        Product source = Product.builder()
                .id(1L).slug("src").productType(ProductType.COFFEE)
                .origin(Origin.builder().id(1L).slug("ethiopia")
                        .translations(List.of(OriginTranslation.builder().language(EN).name("Ethiopia").build()))
                        .build())
                .category(Category.builder().id(2L).slug("coffee")
                        .translations(List.of(CategoryTranslation.builder().language(EN).name("Coffee").build()))
                        .build())
                .build();

        assertThat(fallback.reason(source, source, 0.5, AR)).contains("Ethiopia", "Coffee");
    }

    @Test
    void fallsBackToAnyAvailableTranslationWhenTheLocaleIsUnsupported() {
        Product source = Product.builder()
                .id(1L).slug("src").productType(ProductType.COFFEE)
                .origin(Origin.builder().id(1L).slug("ethiopia")
                        .translations(List.of(OriginTranslation.builder().language(AR).name("إثيوبيا").build()))
                        .build())
                .category(Category.builder().id(2L).slug("coffee")
                        .translations(List.of(CategoryTranslation.builder().language(AR).name("قهوة").build()))
                        .build())
                .build();

        assertThat(fallback.reason(source, source, 0.5, "fr")).contains("إثيوبيا", "قهوة");
    }

    /** A translation row with a blank name is present but unusable as text, so it is skipped. */
    @Test
    void skipsATranslationWhoseNameIsBlank() {
        Product source = Product.builder()
                .id(1L).slug("src").productType(ProductType.COFFEE)
                .origin(Origin.builder().id(1L).slug("ethiopia")
                        .translations(List.of(
                                OriginTranslation.builder().language(EN).name("   ").build(),
                                OriginTranslation.builder().language(EN).name("Ethiopia").build()))
                        .build())
                .category(Category.builder().id(2L).slug("coffee").translations(List.of()).build())
                .build();

        assertThat(fallback.reason(source, source, 0.5, EN)).contains("Ethiopia").doesNotContain("  :");
    }

    @Test
    void aRelationWithNoTranslationsAtAllFallsBackToItsSlug() {
        Product source = Product.builder()
                .id(1L).slug("src").productType(ProductType.COFFEE)
                .origin(Origin.builder().id(1L).slug("ethiopia").translations(List.of()).build())
                .category(Category.builder().id(2L).slug("coffee").translations(List.of()).build())
                .build();

        assertThat(fallback.reason(source, source, 0.5, AR)).contains("ethiopia", "coffee");
    }

    @Test
    void aNullTranslationListFallsBackToTheSlug() {
        Product source = Product.builder()
                .id(1L).slug("src").productType(ProductType.COFFEE)
                .origin(Origin.builder().id(1L).slug("ethiopia").translations(null).build())
                .category(Category.builder().id(2L).slug("coffee").translations(null).build())
                .build();

        assertThat(fallback.reason(source, source, 0.5, AR)).contains("ethiopia", "coffee");
    }

    @Test
    void aNullLanguageIsTreatedAsAnUnknownLocaleRatherThanThrowing() {
        Product source = product(1L, "src", "ethiopia", "coffee");
        Product same = product(2L, "same", "ethiopia", "coffee");

        assertThat(fallback.reason(source, same, 0.4, null)).isNotBlank();
    }

    @Test
    void notesAreListedInSourceOrderAndOnlyWhenShared() {
        Product source = coffee(1L, "src", null, null, null, null, List.of("zulu", "berry"));
        Product same = coffee(2L, "same", null, null, null, null, List.of("berry", "zulu", "extra"));

        assertThat(fallback.reason(source, same, 0.3, EN))
                .contains("Notes: Zulu, Berry")
                .doesNotContain("Extra");
    }

    // --- neutral reason ----------------------------------------------------------------

    @Test
    void neutralReasonIsLocalizedRatherThanHardcodedEnglish() {
        Product source = product(1L, "src", "ethiopia", "coffee");
        Product other = product(3L, "other", "colombia", "merch");

        String arabic = fallback.reason(source, other, 0.0, AR);
        String english = fallback.reason(source, other, 0.0, EN);

        assertThat(arabic).isNotBlank().isNotEqualTo(english).contains("المزيد");
        assertThat(english).isNotBlank();
    }

    @Test
    void neutralReasonIsExposedForCallersThatMustSubstituteTheirOwn() {
        assertThat(fallback.neutralReason(AR)).isNotBlank().contains("المزيد");
        assertThat(fallback.neutralReason(EN)).isNotBlank();
    }
}