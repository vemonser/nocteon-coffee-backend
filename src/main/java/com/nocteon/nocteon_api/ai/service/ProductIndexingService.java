package com.nocteon.nocteon_api.ai.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.nocteon.nocteon_api.ai.dto.response.AiIndexStatsResponse;
import com.nocteon.nocteon_api.ai.dto.response.AiReindexResponse;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Projects the product catalog into the vector store and keeps it in sync.
 *
 * <p>Two entry points, both guarded so the nightly scheduler and the admin reindex
 * endpoint can never overlap:
 * <ul>
 * <li>{@link #indexAll()} rebuilds the whole active catalog, re-embedding only the products
 * whose text actually changed.</li>
 * <li>{@link #upsertById(Long)} re-embeds a single product — the entry point for callers that run
 * after commit and therefore hold nothing but an id; {@link #upsert(Product)} is the same thing
 * from a managed instance.</li>
 * </ul>
 * plus {@link #remove(String)} to drop a deleted product and {@link #stats()} to report drift.
 *
 * <h2>Staleness detection</h2>
 * Every document carries a {@code contentHash} of its own text (see
 * {@link ProductDocumentFactory}). Spring AI 2.0.1's {@link VectorStore} has no get-by-id
 * and no count, so the stored hash is read back by searching with the document's <em>own</em>
 * text and asking for a single hit: identical text embeds identically, so the document's own
 * row is the nearest neighbour of its own text. A product counts as unchanged only when that
 * hit is <em>its</em> document — same {@code contentHash} and same {@code slug} — so another
 * product's row can never stand in for it.
 *
 * <h2>Why the methods are transactional</h2>
 * {@link ProductDocumentFactory} reads {@code category}, {@code origin}, {@code farm},
 * {@code coffeeDetails}, {@code tastingNotes} and {@code brewingMethods}, and only
 * {@code translations}, {@code media} and {@code variants} are join-fetched by
 * {@link ProductRepository#findAllByIdInWithDetails(List)}. The rest stay lazy, so the
 * projection must happen while the persistence context is still open — every public method
 * that touches an entity is therefore {@code @Transactional}, and
 * {@link ProductDocumentFactory#buildText(Product)} is never called on a detached instance.
 * {@link #upsert(Product)} re-reads the product inside that transaction instead of trusting
 * the caller's copy, which is typically detached (callers fire it after commit).
 *
 * <h2>Why {@code readOnly} is absent where this class writes</h2>
 * {@link #indexAll()} and {@link #upsert(Product)} call {@link VectorStore#add(List)}, which
 * PgVectorStore issues as {@code INSERT ... ON CONFLICT (id) DO UPDATE} over a
 * {@code JdbcTemplate} on the application's own {@code DataSource}. Spring's
 * {@code JpaTransactionManager} binds that {@code DataSource} to the transaction and, for a
 * {@code readOnly} definition, pushes the connection into {@code BEGIN READ ONLY} — so a
 * {@code readOnly = true} here would fail every document write with {@code cannot execute
 * INSERT in a read-only transaction}. Only {@link #stats()}, which writes nothing, is marked
 * read-only. Nothing mutates a managed entity on these paths, so dropping the flag costs no
 * dirty-checking safety.
 *
 * <p>The cost is that a run holds a database transaction — and a pooled connection — for as long
 * as the embedding calls take. That is deliberate: the alternative (loading
 * everything up front, then embedding outside the transaction) needs a second collaborator and
 * would put every catalog row in memory at once.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductIndexingService {

    /** Prefix that marks a vector-store document as belonging to a product. */
    private static final String DOCUMENT_ID_PREFIX = "product:";

    /** Metadata key holding the hash of the document's own text. */
    private static final String CONTENT_HASH_KEY = "contentHash";

    /** Metadata key holding the product slug. */
    private static final String SLUG_KEY = "slug";

    /** One hit is enough: the document being looked for is its own text's nearest neighbour. */
    private static final int LOOKUP_TOP_K = 1;

    private final VectorStore vectorStore;
    private final ProductRepository productRepository;
    private final ProductDocumentFactory documentFactory;

    /**
     * Set for the duration of a run so a second caller — the scheduler and the admin endpoint
     * both land here — is turned away instead of embedding the same catalog twice.
     */
    private final AtomicBoolean reindexInProgress = new AtomicBoolean(false);

    /**
     * When the last reindex attempt finished, successful or not. Package-private and volatile so
     * {@link #stats()} can read it from another thread while a run is stamping it.
     */
    volatile Instant lastRunAt;

    /**
     * Embeds every active product whose text changed since it was last indexed.
     *
     * <p>Note the deliberate absence of {@code readOnly = true}: this method writes, because
     * {@link VectorStore#add(List)} is an {@code INSERT ... ON CONFLICT (id) DO UPDATE} issued
     * through a {@code JdbcTemplate} over the application's own {@code DataSource}. Spring's
     * {@code JpaTransactionManager} binds that {@code DataSource} to the transaction, so
     * {@code readOnly = true} pushes the connection into {@code BEGIN READ ONLY} and every
     * document write fails with {@code cannot execute INSERT in a read-only transaction}.
     * {@link #stats()} is the only method here that is genuinely read-only.
     *
     * @return how many documents were written, how many were already current, and how long the
     *         run took; all zeros when another run holds the guard
     */
    @Transactional
    public AiReindexResponse indexAll() {
        if (!reindexInProgress.compareAndSet(false, true)) {
            log.warn("AI reindex already in progress, skipping this run");
            return AiReindexResponse.builder().indexed(0).skipped(0).durationMs(0).build();
        }

        long startNanos = System.nanoTime();
        try {
            List<Document> changed = new ArrayList<>();
            int skipped = 0;

            for (Document document : loadActiveDocuments()) {
                if (isAlreadyIndexed(document)) {
                    skipped++;
                } else {
                    changed.add(document);
                }
            }

            if (!changed.isEmpty()) {
                // One batch: PgVectorStore upserts by id, so re-indexing is idempotent.
                vectorStore.add(List.copyOf(changed));
            }

            long durationMs = elapsedMillis(startNanos);
            log.info("AI reindex finished: indexed={} skipped={} durationMs={}",
                    changed.size(), skipped, durationMs);

            return AiReindexResponse.builder()
                    .indexed(changed.size())
                    .skipped(skipped)
                    .durationMs(durationMs)
                    .build();
        } finally {
            lastRunAt = Instant.now();
            reindexInProgress.set(false);
        }
    }

    /**
     * Re-embeds a single product.
     *
     * <p>Unconditional by design: the caller has just changed the product, so there is nothing
     * to compare against and one extra embedding is cheaper than another round trip to the
     * vector store. The product is re-read from the database rather than projected from the
     * argument, because callers pass detached instances and the projection needs lazy relations.
     *
     * <p>Not {@code readOnly = true} — see {@link #indexAll()} for why writing to the vector store
     * forbids it.
     *
     * @param product the changed product; ignored when it has no id or no longer exists
     */
    @Transactional
    public void upsert(Product product) {
        if (product == null) {
            log.warn("Skipping AI upsert for a null product");
            return;
        }

        upsertById(product.getId());
    }

    /**
     * Re-embeds a single product, identified by id alone.
     *
     * <p>The preferred entry point for a caller that fires its reindex after commit. Its entity is
     * detached by then, so nothing may be read off it beyond the id — the lazy relations
     * {@link ProductDocumentFactory} walks are closed by then — and this signature makes that
     * structural rather than a convention: there is no entity in reach to depend on by accident.
     *
     * <p>Not {@code readOnly = true} — see {@link #indexAll()} for why writing to the vector store
     * forbids it. Invoked from {@link #upsert(Product)} by self-call, so it runs inside that
     * method's transaction; the proxy is bypassed, which is harmless because both definitions are
     * scoped identically and the outer call always opens the transaction.
     *
     * @param productId the persisted id; ignored when null or when the product no longer exists
     */
    @Transactional
    public void upsertById(Long productId) {
        if (productId == null) {
            log.warn("Skipping AI upsert for a product without an id");
            return;
        }

        Product managed = productRepository.findById(productId).orElse(null);
        if (managed == null) {
            log.warn("Skipping AI upsert for product {}: no longer present in the catalog", productId);
            return;
        }

        vectorStore.add(List.of(documentFactory.toDocument(managed)));
        log.debug("AI upsert indexed product {}", managed.getSlug());
    }

    /**
     * Deletes the vector-store document of a product.
     *
     * <p>Uses the id overload deliberately: in Spring AI 2.0.1 {@code delete(String)} parses its
     * argument as a <em>filter expression</em> and deletes by predicate, not by document id.
     *
     * @param slug the product slug; a blank slug is ignored rather than deleting a junk id
     */
    public void remove(String slug) {
        if (!StringUtils.hasText(slug)) {
            log.warn("Skipping AI document removal for a blank slug");
            return;
        }

        vectorStore.delete(List.of(DOCUMENT_ID_PREFIX + slug));
        log.debug("AI removed document for product {}", slug);
    }

    /**
     * Reports how far the vector store has drifted from the active catalog.
     *
     * <p>A product is stale when its current {@code contentHash} is not the one stored, which
     * covers both a stale row and a missing one; {@code indexed} is the remainder. This costs one
     * similarity search per active product, so it is meant for the admin view, not a hot path.
     *
     * @return the counts plus the instant the last reindex attempt finished
     */
    @Transactional(readOnly = true)
    public AiIndexStatsResponse stats() {
        List<Document> documents = loadActiveDocuments();
        int stale = 0;

        for (Document document : documents) {
            if (!isAlreadyIndexed(document)) {
                stale++;
            }
        }

        return AiIndexStatsResponse.builder()
                .indexed(documents.size() - stale)
                .total(documents.size())
                .stale(stale)
                .lastRunAt(lastRunAt)
                .build();
    }

    /**
     * Projects every active product into a document. Must run inside the caller's transaction:
     * the projection walks lazy relations that only load while the session is open.
     */
    private List<Document> loadActiveDocuments() {
        List<Long> ids = productRepository.findAll().stream()
                .filter(Product::isActive)
                .map(Product::getId)
                .toList();

        if (ids.isEmpty()) {
            // Guards the `id IN ()` query, which is not valid SQL.
            return List.of();
        }

        return productRepository.findAllByIdInWithDetails(ids).stream()
                .filter(product -> StringUtils.hasText(product.getSlug()))
                .map(documentFactory::toDocument)
                .toList();
    }

    /**
     * Whether the vector store already holds this exact content for this exact product.
     *
     * <p>Anything short of a confident yes counts as "not indexed" so the document is rewritten:
     * no hit at all, a hit for another product, or a stored document that carries no hash to
     * compare. Being wrong this way costs one embedding; being wrong the other way leaves the
     * store permanently out of date.
     */
    private boolean isAlreadyIndexed(Document document) {
        SearchRequest lookup = SearchRequest.builder()
                .query(document.getText())
                .topK(LOOKUP_TOP_K)
                .build();

        return vectorStore.similaritySearch(lookup).stream()
                .findFirst()
                .filter(stored -> isSameContent(stored, document))
                .isPresent();
    }

    /**
     * A missing hash on either side is never a match: two absent values comparing equal would make
     * every unhashed document look already-indexed. The hash is read off the fresh document with a
     * null check rather than through {@link Objects#equals}, because the fresh document is the one
     * this class just built and an absent hash there is a factory defect worth crashing on
     * loudly at the comparison rather than dereferencing blindly.
     */
    private boolean isSameContent(Document stored, Document fresh) {
        String freshHash = metadataOf(fresh, CONTENT_HASH_KEY);

        return freshHash != null
                && freshHash.equals(metadataOf(stored, CONTENT_HASH_KEY))
                && Objects.equals(metadataOf(stored, SLUG_KEY), metadataOf(fresh, SLUG_KEY));
    }

    private String metadataOf(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
