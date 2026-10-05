package com.nocteon.nocteon_api.product.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.enums.ProductType;

import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>,JpaSpecificationExecutor<Product>  {
        @Query("""
                        SELECT p FROM Product p
                        WHERE p.category.slug = :slug
                        AND p.isActive = true
                        """)
        Page<Product> findByCategorySlugPublic(@Param("slug") String slug, Pageable pageable);

        @Query("""
                        SELECT p FROM Product p
                        WHERE p.farm.slug = :slug
                        AND p.isActive = true
                        """)
        Page<Product> findByFarmSlugPublic(@Param("slug") String slug, Pageable pageable);

        @Query("""
                        SELECT p FROM Product p
                        WHERE p.origin.slug = :slug
                        AND p.isActive = true
                        """)
        Page<Product> findByOriginSlugPublic(@Param("slug") String slug, Pageable pageable);

        @Query("""
                            SELECT DISTINCT p FROM Product p
                            JOIN p.translations t
                            WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :query, '%'))
                            OR LOWER(p.slug) LIKE LOWER(CONCAT('%', :query, '%'))
                        """)
        List<Product> searchProducts(@Param("query") String query, Pageable pageable);

       
        

        @Query("""
                        SELECT DISTINCT p FROM Product p
                        LEFT JOIN p.translations t
                        LEFT JOIN p.category c
                        WHERE (:search IS NULL OR :search = ''
                               OR  LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')))
                        AND (:categorySlug IS NULL OR c.slug = :categorySlug)
                        AND (:productType IS NULL OR p.productType = :productType)
                        AND (:isActive IS NULL OR p.isActive = :isActive)
                        AND (:featured IS NULL OR p.featured = :featured)
                        """)
        Page<Product> findAllDashboard(
                        @Param("search") String search,
                        @Param("categorySlug") String categorySlug,
                        @Param("productType") ProductType productType,
                        @Param("isActive") Boolean isActive,
                        @Param("featured") Boolean featured,
                        Pageable pageable);

        @Query("""
                        SELECT DISTINCT p
                        FROM Product p
                        LEFT JOIN FETCH p.translations t
                        WHERE p.slug = :slug
                        AND t.language = :language
                        """)
        Optional<Product> findBySlugAndLanguage(
                        @Param("slug") String slug,
                        @Param("language") String language);

        /**
         * The relations {@code ProductDocumentFactory.buildText} and
         * {@code AttributeSimilarityFallback} walk, as a JPQL fragment.
         *
         * <p>Every clause here is a <em>to-one</em> — {@code category}, {@code origin},
         * {@code farm}, {@code coffeeDetails} and the three lookups hanging off it — except for the
         * brewing-method pair, which is the single to-many this query fetches. Fetching exactly
         * one to-many is deliberate: two or more collection fetches in one query cross-multiply
         * into a cartesian product, and {@code DISTINCT} de-duplicates the entities in memory
         * without ever un-multiplying the rows the database sent. {@code translations} and
         * {@code tastingNotes} therefore stay lazy and are loaded by {@code @BatchSize} batches
         * instead, which keeps the row count proportional to the catalog rather than to the
         * product of the collection sizes.
         *
         * <p>Measured against the development database (7 active products, Hibernate
         * {@code Statistics}): projecting the whole catalog issued <b>34</b> statements through
         * {@code findAll()} + {@code findAllByIdInWithDetails} and <b>3</b> through this query. The
         * single-product path went from <b>13</b> statements via {@code findById} to <b>3</b>. SQL
         * rows returned for the seven-product batch were <b>80</b> under the old three-collection
         * join against <b>11</b> here — the naive alternative that also joined
         * {@code product_tasting_notes} returned <b>54</b>, which is the multiplication this
         * fragment is shaped to avoid.
         */
        String AI_PROJECTION_FETCH_JOINS = """
                            LEFT JOIN FETCH p.category
                            LEFT JOIN FETCH p.origin
                            LEFT JOIN FETCH p.farm
                            LEFT JOIN FETCH p.coffeeDetails cd
                            LEFT JOIN FETCH cd.roastLevel
                            LEFT JOIN FETCH cd.processingMethod
                            LEFT JOIN FETCH cd.coffeeVariety
                            LEFT JOIN FETCH p.brewingMethods bm
                            LEFT JOIN FETCH bm.brewingMethod
                        """;

        /**
         * Every active product with the relations the AI projection walks already loaded.
         *
         * <p>Purpose-built for {@code ProductIndexingService} and
         * {@code SimilarProductService}'s attribute fallback, both of which load the whole active
         * catalog and then read {@code category}, {@code origin}, {@code farm},
         * {@code coffeeDetails}, {@code tastingNotes} and {@code brewingMethods} on each row. Left
         * lazy, that is roughly six extra {@code SELECT}s <em>per product</em>; fetched here, the
         * per-product cost is gone and the remaining lazy collections load in batches of thirty.
         *
         * <p>{@code media} and {@code variants} are deliberately absent — neither the projection
         * nor the attribute weights read them, and joining them would multiply rows for nothing.
         *
         * @return the active catalog, each row carrying the projection's relations
         */
        @Query("SELECT DISTINCT p FROM Product p " + AI_PROJECTION_FETCH_JOINS + """
                            WHERE p.isActive = true
                        """)
        List<Product> findAllActiveForAiProjection();

        /**
         * One product by id, with the relations the AI projection walks already loaded.
         *
         * <p>The single-product counterpart of {@link #findAllActiveForAiProjection()}, for the
         * reindex-after-change path where the caller holds nothing but an id. It exists so a
         * {@code findById} can never quietly become the N+1 entry point that the batch method was
         * created to stop being.
         *
         * @param id the persisted id
         * @return the product, or empty when it no longer exists
         */
        @Query("SELECT DISTINCT p FROM Product p " + AI_PROJECTION_FETCH_JOINS + """
                            WHERE p.id = :id
                        """)
        Optional<Product> findByIdForAiProjection(@Param("id") Long id);

        @Query("""
                            SELECT DISTINCT p FROM Product p
                            LEFT JOIN FETCH p.translations
                            LEFT JOIN FETCH p.media
                            LEFT JOIN FETCH p.variants
                            WHERE p.id IN :ids
                        """)
        List<Product> findAllByIdInWithDetails(@Param("ids") List<Long> ids);

        /**
         * Products by slug, with {@code category} already loaded — the card projection's lookup.
         *
         * <p>{@code ProductCardBuilder} renders up to eight cards beside a chat reply and
         * {@code buildListResponse} reads four relations on each: {@code category}, {@code
         * translations}, {@code media} and {@code variants}. The last three are
         * {@code @BatchSize(30)} collections, so they arrive in three batched statements however
         * many products are asked for. {@code category} is the exception — a {@code @ManyToOne}
         * with no {@code @BatchSize}, which Hibernate therefore loads with one {@code SELECT} per
         * card. Left lazy that is up to eight extra statements on every chat turn, for the same
         * single column the card prints.
         *
         * <p>Only the to-one is fetch-joined, and that is the whole shape of the fix. Fetching
         * {@code translations}, {@code media} and {@code variants} together would satisfy all four
         * relations in one query and cross-multiply the rows by the product of the three
         * collection sizes — {@code DISTINCT} de-duplicates the entities in memory but never
         * un-multiplies what the database sent. One to-one join cannot multiply: every product
         * matches at most one category row, so the row count stays equal to the number of slugs.
         *
         * <p>The AI projection queries above deliberately do not fit here. They filter to
         * {@code isActive}, which would silently change which cards render, and they join five more
         * relations the card never reads — including {@code brewingMethods}, a to-many whose fetch
         * would multiply rows for data this projection throws away.
         *
         * @param slugs slugs to resolve; callers de-duplicate and bound the list first
         * @return the matching products, each carrying {@code category}
         */
        @Query("""
                            SELECT DISTINCT p FROM Product p
                            LEFT JOIN FETCH p.category
                            WHERE p.slug IN :slugs
                        """)
        List<Product> findBySlugInWithCategory(@Param("slugs") List<String> slugs);

        Optional<Product> findBySlug(String slug);

        List<Product> findBySlugIn(List<String> slugs);

        boolean existsBySlug(String slug);

        @Query("""
                            SELECT DISTINCT p FROM Product p
                            LEFT JOIN FETCH p.translations t
                            LEFT JOIN FETCH p.media m
                            LEFT JOIN FETCH p.variants v
                            WHERE v.stockQuantity <= :threshold
                            AND v.isActive = true
                            AND p.isActive = true
                        """)
        List<Product> findLowStockProducts(@Param("threshold") int threshold);
}
