package com.nocteon.nocteon_api.ai.service;

/**
 * The asymmetric-retrieval prefixes this project's embedding model expects on each side of a
 * vector search.
 *
 * <h2>Why the task intent lives in the text</h2>
 * {@code gemini-embedding-2} does not accept a {@code task_type} parameter — Google's own
 * documentation is explicit that the model has no task-type support and that the intent belongs
 * in the text instead. So the two halves of a retrieval task are expressed by prefixing:
 *
 * <pre>
 *   document: title: none | text: {content}
 *   query:    task: search result | query: {content}
 * </pre>
 *
 * <p>Spring AI's own API cannot do this for us. {@code EmbeddingModel} exposes no task-type
 * setter, and even the provider-specific {@code GoogleGenAiTextEmbeddingOptions} carries
 * {@code taskType} as a single <em>bean-level</em> default: {@code PgVectorStore} calls
 * {@code embeddingModel.embed(query)} for a search and
 * {@code embed(List<Document>, EmbeddingOptions, BatchingStrategy)} for an insert with a default,
 * empty {@code EmbeddingOptions}. One bean, one default — so the same value would be applied to
 * both a query and a document, which is precisely the asymmetry the prefixes exist to express.
 *
 * <h2>Why one class, and why unconditional</h2>
 * Document side and query side must agree or the index is subtly wrong: a document embedded as a
 * document and a query embedded as a query live in different regions of the space, and mixing the
 * two forms degrades every score in the store rather than failing loudly. Keeping both prefixes
 * here means the two call sites reference the same constants, and the pairing is asserted by a
 * test rather than by discipline.
 *
 * <p>Unconditional, with no provider check. Adding one would mean the projection became
 * configuration-dependent, and a document whose text depends on which provider happens to be
 * configured would need re-embedding every time that changed — the opposite of the property this
 * change is trying to establish. The prefix is inert for a symmetric model such as the local
 * {@code nomic-embed-text}: it is just a few tokens of leading text on both sides, which is
 * verified to leave ranking unchanged on the local path.
 *
 * <h2>Why the staleness self-lookup still finds its own row</h2>
 * {@code ProductIndexingService} decides a product is unchanged by searching the store with the
 * document's own text and asking for one hit, relying on identical text embedding identically.
 * That holds because the <em>stored</em> text and the <em>lookup</em> text are both the prefixed
 * document text — {@code Document.getText()} carries the prefix and
 * {@code SimilarProductService} passes {@code buildText}, which carries the same one. Only a real
 * user query goes through {@link #forQuery(String)}.
 */
public final class EmbeddingTaskPrefix {

    /**
     * The document-side prefix.
     *
     * <p>{@code title: none} rather than a real title: Google's format allows it explicitly, and
     * this project's document text already begins with the product name, so a second copy of it
     * would be duplicate content rather than new signal.
     */
    public static final String DOCUMENT = "title: none | text: ";

    /** The query-side prefix. */
    public static final String QUERY = "task: search result | query: ";

    private EmbeddingTaskPrefix() {
    }

    /**
     * Formats text as a document to be indexed.
     *
     * @param text the projected document text, never {@code null}
     * @return the text with the document prefix in front
     */
    public static String forDocument(String text) {
        return DOCUMENT + text;
    }

    /**
     * Formats a user query for retrieval.
     *
     * @param query the user's search text, never {@code null}
     * @return the query with the query prefix in front
     */
    public static String forQuery(String query) {
        return QUERY + query;
    }
}