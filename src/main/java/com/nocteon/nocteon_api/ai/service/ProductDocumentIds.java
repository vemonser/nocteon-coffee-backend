package com.nocteon.nocteon_api.ai.service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.util.StringUtils;

/**
 * The one definition of a product's vector-store document id.
 *
 * <h2>Why the id has to be a UUID</h2>
 * Spring AI 2.0.1's {@code PgVectorStore} parses every {@link org.springframework.ai.document.Document}
 * id with {@link UUID#fromString(String)} before writing it, because the {@code vector_store}
 * table's primary key is a {@code uuid} column. A human-readable id such as
 * {@code product:ethiopia-yirgacheffe} therefore fails the very first write with
 * {@code IllegalArgumentException: Invalid UUID string} — the store accepts nothing else, and no
 * amount of mocking hides that.
 *
 * <h2>Why it has to be deterministic</h2>
 * Three behaviours collapse onto a single stable id:
 * <ul>
 * <li>{@link ProductIndexingService#indexAll()} and {@link ProductIndexingService#upsert(Product)}
 * upsert by id, so re-indexing the same product must land on the same row instead of duplicating
 * it;</li>
 * <li>{@link ProductIndexingService#remove(String)} deletes by id, so it must compute exactly the id
 * that indexing wrote or a deleted product's vector lingers forever;</li>
 * <li>the nightly scheduler and the admin reindex endpoint run in the same process against the same
 * catalog, so the mapping cannot depend on in-memory state either.</li>
 * </ul>
 * A random UUID per call satisfies the store and breaks all three. So the id is a name-based
 * (version 3, MD5) UUID over the stable {@code "product:" + slug} name: the same slug always maps
 * to the same UUID, in this process and in the next one, while distinct slugs stay distinct.
 *
 * <h2>Where the human-readable identity went</h2>
 * Nothing was lost: the id is opaque now, so {@code ProductDocumentFactory.buildMetadata} carries
 * the mapping back to the product as {@code slug} and {@code productId}. Every consumer —
 * {@link AiSearchService}, {@link SimilarProductService}, {@code ProductIndexingService}'s
 * staleness check — reads identity from metadata, so none of them can be reading the id by accident.
 */
public final class ProductDocumentIds {

    /**
     * Name prefix that keeps a product document's name from colliding with any other namespace's.
     *
     * <p>Persisted-data contract: it is hashed into every id in the {@code vector_store} table, so
     * changing this string or the {@link StandardCharsets#UTF_8} encoding below orphans every
     * already-indexed row and inserts a duplicate per product on the next re-index. Nothing
     * reconciles ids absent from the active catalog, so those orphans would persist indefinitely
     * and stay invisible to slug-metadata joins.
     *
     * <p>{@code ProductDocumentIdsTest} pins one known slug to its known UUID literal to freeze this
     * the same way a Flyway migration checksum is frozen. Update that literal in the same commit as
     * any change here, and expect a full re-index.
     */
    private static final String NAME_PREFIX = "product:";

    private ProductDocumentIds() {
    }

    /**
     * The vector-store document id for a product slug.
     *
     * <p>Stable across restarts and re-indexes by construction: the value is a pure function of the
     * slug, so {@code upsert} overwrites and {@code remove} deletes the same row.
     *
     * @param slug the product slug; must be non-blank, since a blank slug would map every junk
     *             product onto one shared id
     * @return the id, always parseable by {@link UUID#fromString(String)}
     */
    public static String forSlug(String slug) {
        if (!StringUtils.hasText(slug)) {
            // Enforced here rather than at three call sites: a blank slug is what collapses every
            // malformed product onto one shared id, and forSlug is the only place that mapping is
            // defined. ProductIndexingService already guards remove() and loadActiveDocuments();
            // upsertById does not, so a guard lived only in prose.
            throw new IllegalArgumentException("product slug must be non-blank to derive a document id");
        }
        String name = NAME_PREFIX + slug;
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
    }
}