package com.nocteon.nocteon_api.product.repository;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shape of the AI projection queries, asserted without a database.
 *
 * <h2>Why this reads the annotation instead of counting queries</h2>
 * The N+1 this guards against is only observable against a live database with
 * {@code HibernateStatistics} enabled, and this project's integration test boots the full
 * application context, which needs {@code pgvector} — unavailable here. Reading the {@code @Query}
 * text is the next best thing and it pins the two properties that actually matter:
 *
 * <ol>
 * <li>every relation the projection reads is either join-fetched or {@code @BatchSize}d, so no
 * product costs an extra {@code SELECT}; and</li>
 * <li>at most <em>one</em> to-many is join-fetched.</li>
 * </ol>
 *
 * <p>Point two is the reason this test exists rather than a plain "the fetch joins are there"
 * assertion. Two collection fetches in one query cross-multiply: joining {@code translations} and
 * {@code tastingNotes} together makes the database return the product of the two collection sizes
 * in rows, and {@code DISTINCT} de-duplicates the entities in memory without ever un-multiplying
 * the rows that crossed the wire. Exactly one to-many is fetched, and the rest are batch-loaded —
 * which is why the fetched one is the small {@code brewingMethods} pair rather than
 * {@code translations}.
 *
 * <p>Reflection also catches a rename that a Mockito test cannot: {@code buildText} would still
 * return a passing result from a hand-built {@link com.nocteon.nocteon_api.product.entity.Product}
 * even after the relation it walks stopped being fetched.
 */
class ProductAiProjectionQueryTest {

    /** Relations {@code ProductDocumentFactory.buildText} walks, as {@code @Query} fetch paths. */
    private static final List<String> TO_ONES_BUILT_TEXT_WALKS = List.of(
            "p.category",
            "p.origin",
            "p.farm",
            "p.coffeeDetails",
            "p.brewingMethods");

    /** The lookup relations hanging off {@code coffeeDetails}, reached through the alias. */
    private static final List<String> COFFEE_DETAILS_LOOKUPS = List.of(
            "cd.roastLevel",
            "cd.processingMethod",
            "cd.coffeeVariety");

    /**
     * Every to-many on {@code Product}. Fetching more than one of these in a single query is the
     * cartesian-product hazard, so the assertion below is written over this whole list rather than
     * over the relations the projection happens to need — a future edit that adds a fourth fetch
     * join cannot slip past it.
     */
    private static final List<String> ALL_TO_MANY_ON_PRODUCT = List.of(
            "p.translations",
            "p.variants",
            "p.media",
            "p.tastingNotes",
            "p.pairings",
            "p.brewingMethods",
            "p.journalPosts");

    private static String queryOf(String methodName) {
        Method method = method(methodName);
        Query query = method.getAnnotation(Query.class);

        assertThat(query).as("%s must carry a @Query", methodName).isNotNull();

        // Collapse the whitespace the text blocks carry, so the assertions read as the query does.
        return query.value().replaceAll("\\s+", " ").trim();
    }

    private static Method method(String name) {
        return Arrays.stream(ProductRepository.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("ProductRepository has no method " + name));
    }

    /**
     * Fetch-joined paths named in {@code query}, with any alias stripped.
     *
     * <p>Aliases are dropped because they are an implementation detail of this particular query —
     * {@code cd} for {@code p.coffeeDetails} here, a different name elsewhere — while the relation
     * path is what has to stay stable. Case is left alone so the expected paths can be written the
     * way the entity fields are actually spelled.
     */
    private static List<String> fetchJoinsIn(String query) {
        return List.of(query.split("(?i)left join fetch ")).stream()
                .skip(1)
                .map(join -> join.split("(?i) on | where ")[0].trim().split("\\s+")[0])
                .toList();
    }

    @Test
    void theBatchQueryJoinFetchesEveryToOneTheProjectionReads() {
        List<String> fetched = fetchJoinsIn(queryOf("findAllActiveForAiProjection"));

        assertThat(fetched).containsAll(TO_ONES_BUILT_TEXT_WALKS);
        assertThat(fetched).containsAll(COFFEE_DETAILS_LOOKUPS);
    }

    @Test
    void theSingleProductQueryJoinFetchesTheSameRelations() {
        // If the two queries ever drift, the upsert path silently becomes the N+1 one — which is
        // exactly how the original bug got in: the batch path was fixed and the single-product one
        // was left calling a plain findById.
        assertThat(fetchJoinsIn(queryOf("findByIdForAiProjection")))
                .containsExactlyElementsOf(fetchJoinsIn(queryOf("findAllActiveForAiProjection")));
    }

    /**
     * The hazard, stated as a test: at most one to-many may be join-fetched.
     *
     * <p>A second one would multiply the row count by the size of both collections. The two
     * relations the projection needs most here — {@code translations} and {@code tastingNotes} —
     * are therefore expected to be absent from the fetch list and to arrive by {@code @BatchSize}
     * instead, which is asserted separately.
     */
    @Test
    void atMostOneToManyIsJoinFetched() {
        for (String methodName : List.of("findAllActiveForAiProjection", "findByIdForAiProjection")) {
            List<String> fetchedToMany = fetchJoinsIn(queryOf(methodName)).stream()
                    .filter(ALL_TO_MANY_ON_PRODUCT::contains)
                    .toList();

            assertThat(fetchedToMany)
                    .as("%s must not join-fetch more than one to-many — two cross-multiply the row count", methodName)
                    .hasSizeLessThanOrEqualTo(1);
        }
    }

    /** The batch-loaded half of the fix: these two are never fetched, so they must be batched. */
    @Test
    void theUnfetchedCollectionsAreBatchLoadedOnTheEntity() {
        assertThat(org.hibernate.annotations.BatchSize.class)
                .isNotNull(); // compile-time proof the annotation is on the classpath at all

        assertThat(batchSizeOf("translations")).isEqualTo(30);
        assertThat(batchSizeOf("tastingNotes")).isEqualTo(30);
    }

    private static Integer batchSizeOf(String field) {
        try {
            var fieldRef = com.nocteon.nocteon_api.product.entity.Product.class.getDeclaredField(field);
            var batch = fieldRef.getAnnotation(org.hibernate.annotations.BatchSize.class);

            assertThat(batch).as("Product.%s must be @BatchSize'd, or it is one query per product", field).isNotNull();

            return batch.size();
        } catch (NoSuchFieldException e) {
            throw new AssertionError("Product has no field " + field, e);
        }
    }

    /** The active filter moved into the query, replacing the in-memory {@code isActive} check. */
    @Test
    void theBatchQueryFiltersInactiveProductsInSql() {
        assertThat(queryOf("findAllActiveForAiProjection"))
                .contains("WHERE p.isActive = true");
    }

    /**
     * {@code media} and {@code variants} are deliberately not fetched: neither the document
     * projection nor the attribute weights read them, and fetching them would only multiply rows.
     */
    @Test
    void theUnneededCollectionsAreNotFetched() {
        List<String> fetched = fetchJoinsIn(queryOf("findAllActiveForAiProjection"));

        assertThat(fetched).doesNotContain("p.media", "p.variants", "p.pairings", "p.journalPosts");
    }

    // --- the chatbot card lookup -----------------------------------------------------------------

    /**
     * The card projection reads four relations per product, and only one of them is not already
     * {@code @BatchSize}d — so this is the whole fix for the per-card {@code SELECT}s.
     *
     * <p>Asserted as a positive ("{@code category} is fetched") plus the batch assertion below,
     * because either half alone is silently satisfiable: {@code category} could be fetch-joined
     * while a collection it does not need joins with it, or the fetch could be dropped entirely
     * while {@link #theCollectionsTheCardProjectionReadsAreBatched()} still passes.
     */
    @Test
    void theCardLookupJoinFetchesTheCategoryTheCardPrints() {
        assertThat(fetchJoinsIn(queryOf("findBySlugInWithCategory"))).contains("p.category");
    }

    /**
     * The hazard, applied to the card lookup. It fetches <em>no</em> to-many, where the projection
     * queries allow one: {@code buildListResponse} reads {@code translations}, {@code media} and
     * {@code variants}, all batchable, so joining any of them would multiply rows to save
     * statements the batches save anyway. Asserting {@code <= 1} here would pass on a query that
     * had regressed into one collection fetch.
     */
    @Test
    void theCardLookupFetchesNoCollectionAtAll() {
        List<String> fetchedToMany = fetchJoinsIn(queryOf("findBySlugInWithCategory")).stream()
                .filter(ALL_TO_MANY_ON_PRODUCT::contains)
                .toList();

        assertThat(fetchedToMany)
                .as("the card lookup must fetch only the to-one — the three collections it reads are all @BatchSize'd")
                .isEmpty();
    }

    /** The batched half of the card fix: without these, each is one {@code SELECT} per card. */
    @Test
    void theCollectionsTheCardProjectionReadsAreBatched() {
        assertThat(batchSizeOf("translations")).isEqualTo(30);
        assertThat(batchSizeOf("media")).isEqualTo(30);
        assertThat(batchSizeOf("variants")).isEqualTo(30);
    }

    /**
     * The lookup filters on slug only.
     *
     * <p>Pinned because reusing {@code findAllActiveForAiProjection} here would look right — it
     * already join-fetches {@code category} — while silently dropping every inactive product from
     * the rail before {@code ProductCardBuilder} can decide to drop it. The builder's own
     * {@code isActive} filter stays the one that decides.
     */
    @Test
    void theCardLookupDoesNotFilterOnActive() {
        String query = queryOf("findBySlugInWithCategory");

        assertThat(query).contains("p.slug IN :slugs");
        assertThat(query).doesNotContain("isActive");
    }
}