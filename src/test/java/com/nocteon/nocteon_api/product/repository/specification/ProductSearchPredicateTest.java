package com.nocteon.nocteon_api.product.repository.specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaExpression;
import org.hibernate.query.criteria.JpaFunction;
import org.hibernate.query.criteria.JpaOrder;
import org.hibernate.query.criteria.JpaPredicate;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import com.nocteon.nocteon_api.product.dto.request.ProductFilterRequest;
import com.nocteon.nocteon_api.product.entity.Product;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * The widened keyword search, asserted as predicate <em>structure</em> rather than as result rows.
 *
 * <h2>Why the structure and not the results</h2>
 *
 * A {@link Specification} only produces SQL when Hibernate walks it, which needs a live database.
 * The one integration test in this project boots the whole context, and it needs {@code pgvector},
 * which this machine has not got. So instead of counting rows these tests record the criteria tree
 * as it is assembled — every join, every {@code LIKE} pattern, every subquery, every
 * {@code groupBy}/{@code distinct} — and assert on that. It is the technique
 * {@code ProductAiProjectionQueryTest} uses on the {@code @Query} text, one layer further down.
 *
 * <h2>What is being pinned, in priority order</h2>
 *
 * <ol>
 * <li><b>The defect is gone.</b> {@code "ethiopian coffee"} reaches {@code origins.slug} and
 * {@code "chocolatey"} reaches {@code tasting_notes.slug}. Neither is a substring of the product
 * name — and on the {@code ar} locale the name is Arabic, so the old predicate matched nothing at
 * all.</li>
 * <li><b>No row explosion.</b> {@code tastingNotes} and {@code brewingMethods} are to-many;
 * joining them for a search term would multiply the rows pagination counts, which is exactly what
 * {@code groupBy(products.id)} and {@code distinct} exist to absorb. The outer join list is asserted
 * to be <em>exactly</em> the five it was before this change.</li>
 * <li><b>Ordering and paging are untouched.</b> Every branch of {@code applyOrdering} is asserted to
 * behave identically with and without search text.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ProductSearchPredicateTest {

    /** The {@code Subquery} methods whose return value is the subquery itself. */
    private static final java.util.Set<String> SUBLQUERY_CHAINED =
            java.util.Set.of("select", "where", "groupBy", "having");

    /** One {@code LIKE} as it was assembled: the attribute it was written against, and the pattern. */
    private record Like(String path, String pattern) {
    }

    // --- tokenisation -------------------------------------------------------------------

    /**
     * The defect in one line: the old predicate handed the whole query to a single {@code LIKE}
     * against the product name, so a multi-word query could only match a product literally named
     * after the sentence. Splitting it is the precondition for everything else.
     */
    @Test
    void aMultiWordQueryBecomesSeparateTerms() {
        assertThat(ProductSearchPredicate.tokenize("ethiopian coffee"))
                .containsExactly("ethiopian", "coffee");
    }

    /** Case and punctuation are noise; a shopper typing {@code "Yirgacheffe!!"} means Yirgacheffe. */
    @Test
    void termsAreLowercasedAndStrippedOfPunctuation() {
        assertThat(ProductSearchPredicate.tokenize("  Yirgacheffe,  washed!! "))
                .containsExactly("yirgacheffe", "washed");
    }

    /**
     * Arabic must survive the split intact. The separator class is Unicode-aware and normalisation
     * runs first, so a query pasted from a phone still compares against the ASCII the catalog
     * stores.
     */
    @Test
    void arabicTermsSplitWithoutBeingMangled() {
        assertThat(ProductSearchPredicate.tokenize("قهوة إثيوبية"))
                .containsExactly("قهوة", "إثيوبية");
    }

    /** One-letter fragments are not search terms; keeping them would only widen the OR. */
    @Test
    void singleCharacterFragmentsAreNotTerms() {
        assertThat(ProductSearchPredicate.tokenize("a b x y coffee")).containsExactly("coffee");
    }

    @Test
    void blankAndEmptyQueriesProduceNoTerms() {
        assertThat(ProductSearchPredicate.tokenize(null)).isEmpty();
        assertThat(ProductSearchPredicate.tokenize("   ")).isEmpty();
        assertThat(ProductSearchPredicate.tokenize("!!!")).isEmpty();
    }

    /** Duplicate words must not build duplicate subqueries for the same product. */
    @Test
    void repeatedWordsAreOnlySearchedOnce() {
        assertThat(ProductSearchPredicate.tokenize("coffee COFFEE coffee")).containsExactly("coffee");
    }

    // --- stop words ---------------------------------------------------------------------

    /**
     * {@code "best for espresso"} must reduce to the one word that names an attribute. Left as three
     * terms it is unsatisfiable: no product is called "best", "for", or anything.
     */
    @Test
    void fillerWordsAreDroppedSoTheRemainingTermCanMatch() {
        assertThat(ProductSearchPredicate.significantTerms("best for espresso"))
                .containsExactly("espresso");
    }

    @Test
    void arabicFillerWordsAreDroppedToo() {
        assertThat(ProductSearchPredicate.significantTerms("مناسب للإسبريسو"))
                .containsExactly("للإسبريسو");
    }

    /**
     * {@code coffee} is deliberately <em>not</em> filler. It is the {@code categories} slug, so
     * keeping it is what stops {@code "ethiopian coffee"} from also returning the espresso machine.
     */
    @Test
    void coffeeIsKeptBecauseItIsTheCategorySlug() {
        assertThat(ProductSearchPredicate.significantTerms("ethiopian coffee"))
                .containsExactly("ethiopian", "coffee");
    }

    /**
     * When every word is filler, dropping them all would turn the query into "no search at all" and
     * hand the caller the entire catalog — a wider and more misleading answer than a match on the
     * words the shopper actually typed.
     */
    @Test
    void anAllFillerQueryFallsBackToItsOwnWords() {
        assertThat(ProductSearchPredicate.significantTerms("for the")).containsExactly("for", "the");
    }

    /**
     * The predicate grows with every term, so an unbounded query is an unbounded {@code WHERE}
     * clause. The cap keeps it finite; what survives is what the shopper typed first.
     */
    @Test
    void theTermCountIsCapped() {
        List<String> terms = ProductSearchPredicate.significantTerms(
                "alpha bravo charlie delta echo foxtrot golf");

        assertThat(terms).hasSizeLessThanOrEqualTo(5);
        assertThat(terms).startsWith("alpha", "bravo");
    }

    // --- LIKE patterns ------------------------------------------------------------------

    /**
     * {@code "ethiopian"} is not a substring of {@code origins.slug = 'ethiopia'}, and no amount of
     * substring matching will ever find it. The prefix pattern is what makes design goal 1 reachable:
     * {@code ethi%} is a prefix of {@code ethiopia}.
     */
    @Test
    void aTermAlsoMatchesAPrefixOfTheStoredWord() {
        assertThat(ProductSearchPredicate.likePatterns("ethiopian"))
                .containsExactly("%ethiopian%", "ethi%");
    }

    /** Design goal 2 in one assertion: "chocolatey" is not a substring of "chocolate" either. */
    @Test
    void aFlavourWordMatchesItsTastingNote() {
        assertThat(ProductSearchPredicate.likePatterns("chocolatey")).contains("choc%");
    }

    /** Short terms get the substring only: prefixing a three-letter word would match everything. */
    @Test
    void shortTermsGetNoPrefixPattern() {
        assertThat(ProductSearchPredicate.likePatterns("tea")).containsExactly("%tea%");
    }

    /**
     * Arabic glues prepositions and the definite article onto the front of a noun.
     * {@code بالشوكولاتة} is {@code شوكولاتة} with both, and only the stripped form matches the
     * tasting note stored as {@code شوكولاتة}.
     */
    @Test
    void arabicCliticsAreStrippedSoTheBareNounMatches() {
        assertThat(ProductSearchPredicate.likePatterns("بالشوكولاتة")).contains("%شوكولاتة%");
    }

    /** Stripping must remove the clitic, not consume the word. */
    @Test
    void cliticStrippingLeavesAMatchableStem() {
        assertThat(ProductSearchPredicate.likePatterns("كوب")).contains("%كوب%");
    }

    // --- predicate structure -----------------------------------------------------------

    /**
     * Design goal 1, end to end. Both terms must survive into the predicate, ANDed — otherwise
     * "ethiopian coffee" degenerates into "any coffee" and the origin stops narrowing anything.
     */
    @Test
    void everyTermMustMatchSomewhereAndTermsAreAndedTogether() {
        Recorded recorded = search("ethiopian coffee");

        assertThat(recorded.andSizes).contains(2);
        assertThat(recorded.likes)
                .extracting(Like::pattern)
                .contains("%ethiopian%", "ethi%", "%coffee%", "coff%");
    }

    /**
     * One term, one OR across every attribute a product can be described by: seven attributes read
     * off the joins that already exist, plus six {@code EXISTS} branches for the relations that
     * cannot be joined (origin, farm, category, the three coffee lookups, tasting notes, brewing
     * methods — the coffee lookups share one subquery because an existence check does not care how
     * many rows its own joins produce).
     *
     * <p>Pinned as a count so a relation quietly dropped out of the OR is a failing test rather
     * than a search that silently stops finding things.
     */
    @Test
    void aTermIsOredAcrossEverySearchableAttribute() {
        Recorded recorded = search("ethiopian");

        assertThat(recorded.orSizes).contains(13);
    }

    /** The attributes that carry the answer. Each is reached, for the same term, in that one OR. */
    @Test
    void theSearchReachesOriginFarmCategoryAndProductSlugs() {
        Recorded recorded = search("ethiopian");

        assertLikeOn(recorded, "origin.slug");
        assertLikeOn(recorded, "farm.slug");
        assertLikeOn(recorded, "category.slug");
        assertLikeOn(recorded, "slug");
        assertLikeOn(recorded, "translations.name");
    }

    /** Convenience: "this attribute is matched, with a pattern the query term can reach". */
    private static void assertLikeOn(Recorded recorded, String path) {
        assertThat(recorded.likes)
                .as("%s must be searched, and the term must reach it", path)
                .anyMatch(like -> like.path().equals(path) && like.pattern().equals("ethi%"));
    }

    /**
     * The short and long descriptions are free text sitting on the join the specification already
     * has, so they cost nothing and are where a real catalog's flavour prose would live.
     */
    @Test
    void theSearchAlsoReadsTheTranslatedDescription() {
        Recorded recorded = search("yirgacheffe", "ar");

        assertThat(recorded.likes)
                .anyMatch(l -> l.path().equals("translations.shortDescription"))
                .anyMatch(l -> l.path().equals("translations.description"));
    }

    /** Design goal 2: flavour is described by tasting notes and by the roast and process lookups. */
    @Test
    void tastingNotesRoastProcessAndVarietyAreReachable() {
        Recorded recorded = search("chocolatey");

        // Asserted by attribute path rather than by entity class, because that is what the
        // predicate must use: on Hibernate 7 (Spring Boot 4) `join(RoastLevel.class, JoinType.LEFT)`
        // compiles, passes every test in this file, and then throws
        //   SemanticException: Entity join did not specify a join condition
        // at runtime — taking GET /api/products?search=... to HTTP 500 for every caller. The
        // attribute form resolves the same relations; the class form does not run.
        assertThat(recorded.subqueryEntities)
                .as("the subquery still starts from Product and CoffeeDetails")
                .contains("Product", "CoffeeDetails");
        assertThat(recorded.subqueryJoins)
                .as("tasting notes, the three coffee lookups and their translations are all reached")
                .contains("tastingNotes", "roastLevel", "processingMethod", "coffeeVariety",
                        "translations");
    }

    /** Design goal 3: a brew method, by slug and by translated name. */
    @Test
    void brewingMethodsAreReachable() {
        Recorded recorded = search("espresso");

        assertThat(recorded.subqueryEntities).contains("Product");
        assertThat(recorded.subqueryJoins)
                .as("the brew method is reached through the link entity and its translations, "
                        + "by attribute path for the Hibernate 7 reason above")
                .contains("brewingMethods", "brewingMethod", "translations");
    }

    /** Origin, farm and category names are matched translated, which is the locale-aware half. */
    @Test
    void originFarmAndCategoryNamesAreMatchedThroughTheirTranslations() {
        Recorded recorded = search("ethiopian");

        assertThat(recorded.subqueryEntities)
                .contains("OriginTranslation", "FarmTranslation", "CategoryTranslation");
    }

    /**
     * Design goal 4. Every translation the search reads is pinned to the requested locale, so an
     * {@code ar} query cannot be answered from an English name. Without the pin a search on the
     * wrong locale would quietly match anything the catalog has in any language.
     */
    @Test
    void everyTranslationTheSearchReadsIsPinnedToTheRequestedLocale() {
        Recorded recorded = search("قهوة", "ar");

        assertThat(recorded.localesComparedTo).containsOnly("ar");
        assertThat(recorded.localesComparedTo).isNotEmpty();
    }

    /** The slug branches are the deliberate exception: a slug means the same thing in any locale. */
    @Test
    void slugBranchesAreNotLocalePinned() {
        Recorded recorded = search("ethiopian", "ar");

        assertThat(recorded.likes).anyMatch(l -> l.path().equals("origin.slug"));
    }

    // --- no row explosion --------------------------------------------------------------

    /**
     * Design goal 5, stated as a test: the outer {@code FROM} clause must be the one the endpoint
     * already had. {@code tastingNotes} and {@code brewingMethods} are to-many, and joining them for
     * a search term would multiply the rows pagination counts before {@code groupBy} or
     * {@code distinct} ever sees them.
     */
    @Test
    void theSearchAddsNoJoinsToTheOuterQuery() {
        assertThat(search("ethiopian coffee").outerJoins)
                .containsExactly("translations", "variants", "category", "origin", "farm");
    }

    /** ...and the same holds for a query whose terms only exist in the multi-valued relations. */
    @Test
    void aQueryAgainstTheMultiValuedRelationsStillAddsNoOuterJoins() {
        assertThat(search("chocolatey espresso").outerJoins)
                .containsExactly("translations", "variants", "category", "origin", "farm");
    }

    /**
     * The multi-valued relations are reached by {@code EXISTS}, which filters rows without producing
     * them. Asserted as a shape rather than a count, so adding a searchable relation later cannot
     * quietly turn back into a join.
     */
    @Test
    void theMultiValuedRelationsAreReachedBySubqueryNotByJoin() {
        Recorded recorded = search("chocolatey espresso");

        assertThat(recorded.outerJoins).doesNotContain("tastingNotes", "brewingMethods");
        assertThat(recorded.existsCount).isPositive();
        assertThat(recorded.subqueryJoins).contains("tastingNotes", "brewingMethods");
    }

    // --- ordering and paging are unchanged ----------------------------------------------

    /** The default sort still emits {@code distinct}, which is what absorbs the join fan-out. */
    @Test
    void theDefaultSortStillUsesDistinctWithASearch() {
        Recorded recorded = search("ethiopian");

        assertThat(recorded.distinct).isTrue();
        assertThat(recorded.groupByCalls).isZero();
    }

    /** Price sort still aggregates the variant join and groups by the product. */
    @Test
    void priceSortingStillGroupsByTheProductWithASearch() {
        Recorded recorded = search("ethiopian", "en", filter -> filter.setSort("price"));

        assertThat(recorded.groupByCalls).isEqualTo(1);
        assertThat(recorded.orderByCalls).isEqualTo(1);
    }

    /**
     * Name sort still aggregates the translation join. This one matters most for the locale
     * constraint: a search that had widened the {@code translations} join past the requested
     * language would make this aggregate read several names per product.
     */
    @Test
    void nameSortingStillAggregatesASingleNameWithASearch() {
        Recorded recorded = search("ethiopian", "ar", filter -> filter.setSort("name"));

        assertThat(recorded.groupByCalls).isEqualTo(1);
        assertThat(recorded.nameAggregates).isEqualTo(1);
        assertThat(recorded.localesComparedTo).containsOnly("ar");
    }

    /** The brew-method filter's own join and its score ordering must survive alongside a search. */
    @Test
    void theBrewingMethodFilterStillJoinsAndOrdersWithASearch() {
        Recorded recorded = search("ethiopian", "en", filter -> filter.setBrewingMethodSlug("v60"));

        assertThat(recorded.outerJoins).contains("brewingMethods");
        assertThat(recorded.groupByCalls).isEqualTo(1);
    }

    // --- the other filters are untouched ------------------------------------------------

    /** Facets must still constrain the result, not be replaced by the widened search. */
    @Test
    void facetFiltersAreStillAppliedAlongsideTheSearch() {
        Recorded recorded = search("ethiopian", "en", filter -> {
            filter.setOriginSlug("ethiopia");
            filter.setCategorySlug("coffee");
            filter.setInStockOnly(true);
            filter.setMinPrice(new BigDecimal("100"));
            filter.setOnSaleOnly(true);
        });

        assertThat(recorded.comparisons).contains(
                "origin.slug=ethiopia",
                "category.slug=coffee",
                "variants.price>=100",
                "variants.stockQuantity>0",
                "variants.compareAtPrice IS NOT NULL");
    }

    /** The rating subquery is the pre-existing one; a search must not disturb it. */
    @Test
    void theRatingSubqueryIsUntouchedByTheSearch() {
        Recorded recorded = search("ethiopian", "en", filter -> filter.setMinRating(4.0));

        assertThat(recorded.subqueryEntities).contains("Review");
        assertThat(recorded.outerJoins)
                .containsExactly("translations", "variants", "category", "origin", "farm");
    }

    /** The option-value subquery, likewise. */
    @Test
    void theOptionValueSubqueryIsUntouchedByASearch() {
        Recorded recorded = search("ethiopian", "en", filter -> filter.setOptionValueIds(List.of(1L, 2L)));

        assertThat(recorded.subqueryEntities).contains("ProductVariant");
        assertThat(recorded.subqueryJoins).contains("optionValues");
        assertThat(recorded.outerJoins)
                .containsExactly("translations", "variants", "category", "origin", "farm");
    }

    /** No search text means no search predicate and no extra subqueries at all. */
    @Test
    void aRequestWithoutSearchTextBuildsNothingExtra() {
        Recorded recorded = search(null);

        assertThat(recorded.likes).isEmpty();
        assertThat(recorded.subqueryCount).isZero();
        assertThat(recorded.outerJoins)
                .containsExactly("translations", "variants", "category", "origin", "farm");
    }

    /** A punctuation-only query has no searchable term, and must not become "match everything". */
    @Test
    void aPunctuationOnlyQueryAddsNoSearchPredicate() {
        assertThat(search("!!!").andSizes).containsOnly(3);
    }

    // --- harness -----------------------------------------------------------------------

    private static Recorded search(String query) {
        return search(query, "en", filter -> {
        });
    }

    private static Recorded search(String query, String language) {
        return search(query, language, filter -> {
        });
    }

    private static Recorded search(String query, String language, Consumer<ProductFilterRequest> customizer) {
        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setSearch(query);
        customizer.accept(filter);

        Recorded recorded = new Recorded();
        CriteriaBuilder cb = recorded.criteriaBuilder();
        CriteriaQuery<Object> criteriaQuery = recorded.criteriaQuery();
        Root<Product> root = recorded.outerRoot();

        ProductSpecifications.withFilters(filter, language, 0).toPredicate(root, criteriaQuery, cb);

        return recorded;
    }

    /**
     * A criteria tree that records itself as it is assembled.
     *
     * <p>Every {@code get} returns a mock labelled with its own path, so a {@code LIKE} can be
     * traced back to the attribute it was written against — the only way to assert that the search
     * reached {@code origins.slug} rather than, say, the farm hanging off it. Joins are split into
     * outer and subquery lists, because that split is the row-explosion property itself.
     */
    private static final class Recorded {

        private final Map<Object, String> paths = new IdentityHashMap<>();
        private final List<String> outerJoins = new ArrayList<>();
        private final List<String> subqueryJoins = new ArrayList<>();
        private final List<String> subqueryEntities = new ArrayList<>();
        private final List<Like> likes = new ArrayList<>();
        private final List<Integer> andSizes = new ArrayList<>();
        private final List<Integer> orSizes = new ArrayList<>();
        private final List<String> localesComparedTo = new ArrayList<>();
        private final List<String> comparisons = new ArrayList<>();

        private int subqueryCount;
        private int groupByCalls;
        private int orderByCalls;
        private int existsCount;
        private int nameAggregates;
        private boolean distinct;

        // --- the criteria mocks -----------------------------------------------------

        /**
         * Mocked as a {@link HibernateCriteriaBuilder} because {@code applyOrdering} casts to it for
         * the name sort — mocking the plain interface would make that cast, and with it the one
         * ordering branch that guards the locale, untestable.
         */
        CriteriaBuilder criteriaBuilder() {
            return mock(HibernateCriteriaBuilder.class,
                    withSettings().defaultAnswer(this::onBuilderCall));
        }

        CriteriaQuery<Object> criteriaQuery() {
            return mock(CriteriaQuery.class, withSettings().defaultAnswer(invocation -> {
                switch (invocation.getMethod().getName()) {
                    case "subquery" -> {
                        subqueryCount++;
                        return subquery();
                    }
                    case "groupBy" -> {
                        groupByCalls++;
                        return invocation.getMock();
                    }
                    case "orderBy" -> {
                        orderByCalls++;
                        return invocation.getMock();
                    }
                    case "distinct" -> {
                        distinct = (Boolean) invocation.getArgument(0);
                        return invocation.getMock();
                    }
                    default -> {
                        return RETURNS_DEFAULTS.answer(invocation);
                    }
                }
            }));
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        Root<Product> outerRoot() {
            return (Root<Product>) (Object) mock(Root.class,
                    withSettings().defaultAnswer(targetAnswer("", outerJoins)));
        }

        private Subquery<Long> subquery() {
            // Its own root, its own joins: everything reached from here is inner and cannot
            // multiply the rows the outer query paginates over.
            Root<Object> root = mock(Root.class, withSettings()
                    .defaultAnswer(targetAnswer("subquery[" + subqueryCount + "]", subqueryJoins)));

            return mock(Subquery.class, withSettings().defaultAnswer(invocation -> {
                String method = invocation.getMethod().getName();
                if (method.equals("from")) {
                    subqueryEntities.add(((Class<?>) invocation.getArgument(0)).getSimpleName());
                    return root;
                }
                // `select` and `where` are chainable and are never read back; everything else —
                // `toString` above all, which `pathOf` leans on — goes to the real defaults.
                return SUBLQUERY_CHAINED.contains(method)
                        ? invocation.getMock()
                        : RETURNS_DEFAULTS.answer(invocation);
            }));
        }

        /**
         * The shared behaviour of every {@code Root} and {@code Join}: label attributes, and record
         * joins into whichever list tells outer queries apart from subquery ones.
         */
        private org.mockito.stubbing.Answer<Object> targetAnswer(String label, List<String> joins) {
            return invocation -> {
                switch (invocation.getMethod().getName()) {
                    case "get" -> {
                        Path<Object> path = mock(Path.class);
                        Object attribute = invocation.getArgument(0);
                        paths.put(path, label.isEmpty()
                                ? String.valueOf((Object) attribute)
                                : label + "." + attribute);
                        return path;
                    }
                    case "join" -> {
                        Object target = invocation.getArguments()[0];
                        // `join("x")` names the field; `join(X.class)` names the entity. Both are
                        // recorded, because "which relation did the search open?" has to be
                        // answerable whichever form the code used.
                        String name;
                        if (target instanceof Class<?> type) {
                            name = type.getSimpleName();
                            subqueryEntities.add(name);
                        } else {
                            name = String.valueOf((Object) target);
                        }
                        joins.add(name);
                        return label.isEmpty() ? join(name, joins) : join(label + "." + name, joins);
                    }
                    default -> {
                        return RETURNS_DEFAULTS.answer(invocation);
                    }
                }
            };
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private Join<Object, Object> join(String label, List<String> joins) {
            return (Join) mock(Join.class, withSettings().defaultAnswer(targetAnswer(label, joins)));
        }

        /**
         * Records what {@link ProductSpecifications} asked the builder for, and answers with mocks.
         *
         * <p>The return types are Hibernate's covariant ones — {@code JpaPredicate}, not
         * {@code Predicate} — because {@link HibernateCriteriaBuilder} narrows them and Mockito
         * rejects an answer of the wrong type. That narrowing is not incidental: it is why
         * {@code applyOrdering} can cast to this interface for the name sort.
         */
        private Object onBuilderCall(InvocationOnMock invocation) {
            switch (invocation.getMethod().getName()) {
                case "lower" -> {
                    JpaFunction<String> lowered = mock(JpaFunction.class);
                    paths.put(lowered, pathOf(invocation.getArgument(0)));
                    return lowered;
                }
                case "like" -> {
                    likes.add(new Like(pathOf(invocation.getArgument(0)), invocation.getArgument(1)));
                    return mock(JpaPredicate.class);
                }
                case "and" -> {
                    andSizes.add(arity(invocation));
                    return mock(JpaPredicate.class);
                }
                case "or" -> {
                    orSizes.add(arity(invocation));
                    return mock(JpaPredicate.class);
                }
                case "equal" -> {
                    compare(invocation, "=");
                    return mock(JpaPredicate.class);
                }
                case "ge" -> {
                    compare(invocation, ">=");
                    return mock(JpaPredicate.class);
                }
                case "gt" -> {
                    compare(invocation, ">");
                    return mock(JpaPredicate.class);
                }
                case "le" -> {
                    compare(invocation, "<=");
                    return mock(JpaPredicate.class);
                }
                case "lt" -> {
                    compare(invocation, "<");
                    return mock(JpaPredicate.class);
                }
                case "isNull" -> {
                    compare(invocation, "IS NULL");
                    return mock(JpaPredicate.class);
                }
                case "isNotNull" -> {
                    compare(invocation, "IS NOT NULL");
                    return mock(JpaPredicate.class);
                }
                case "isTrue" -> {
                    return mock(JpaPredicate.class);
                }
                case "exists" -> {
                    existsCount++;
                    return mock(JpaPredicate.class);
                }
                case "least", "greatest" -> {
                    nameAggregates++;
                    return mock(JpaExpression.class);
                }
                case "asc", "desc" -> {
                    return mock(JpaOrder.class);
                }
                case "min", "max", "sum", "avg", "countDistinct" -> {
                    return mock(JpaExpression.class);
                }
                default -> {
                    return mock(JpaPredicate.class);
                }
            }
        }

        /**
         * Records what a comparison compares, because two kinds are load-bearing for this fix: the
         * {@code .language} pins that keep the requested locale, and the facet filters that must
         * survive the widened search.
         *
         * <p>Only the left-hand column is resolved to a path. The right-hand side is often a
         * correlated {@code Join} or {@code Subquery} rather than a value, and rendering that as a
         * name would be noise; comparisons against an actual literal are what the assertions read.
         */
        private void compare(InvocationOnMock invocation, String operator) {
            String path = pathOf(invocation.getArgument(0));
            Object[] arguments = invocation.getArguments();

            if (arguments.length < 2) {
                // `is null` / `is not null` take one argument, and the check itself is the point.
                comparisons.add(path + " " + operator);
                return;
            }

            Object value = arguments[1];
            if (path.endsWith(".language") && value instanceof String locale) {
                localesComparedTo.add(locale);
            }
            if (value instanceof String || value instanceof Number || value instanceof Boolean
                    || value instanceof Enum<?>) {
                comparisons.add(path + operator + value);
            }
        }

        private String pathOf(Object path) {
            String label = paths.get(path);
            return label != null ? label : String.valueOf((Object) path);
        }

        /**
         * How many predicates an {@code and}/{@code or} was called with.
         *
         * <p>{@code Mockito} expands varargs in {@link InvocationOnMock#getArguments()}, so a
         * three-branch {@code or} arrives as three arguments rather than as one array. Reading
         * {@code getArgument(0)} and casting would work by accident for a one-branch call and fail
         * for every other — hence the argument count.
         */
        private int arity(InvocationOnMock invocation) {
            return invocation.getArguments().length;
        }
    }
}