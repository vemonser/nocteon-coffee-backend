package com.nocteon.nocteon_api.ai.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * A stable identity for the embedding model currently configured, mixed into every indexed
 * document's {@code contentHash}.
 *
 * <h2>Why this exists</h2>
 * Two embedding models that both emit 768-dimensional vectors are not interchangeable, and
 * nothing about the stored data says which produced it. Swapping {@code nomic-embed-text} for
 * {@code gemini-embedding-2} leaves every existing row exactly the same width, so the schema still
 * validates, the insert still succeeds, and the search still returns results — every one of them
 * wrong, silently, because a vector from one embedding space has no meaningful cosine distance
 * to a vector from another. The symptom is not an error; it is a storefront quietly returning
 * irrelevant products, which is worse than an outage because nothing alerts.
 *
 * <p>Mixing this fingerprint into the hash makes the ordinary staleness path do the repair.
 * {@code ProductIndexingService} already decides "this document is current" by comparing the
 * stored {@code contentHash} against a freshly computed one, so a changed fingerprint simply
 * makes every stored hash differ from every fresh one and the next {@code indexAll()} re-embeds
 * the whole catalog. Nothing about the staleness algorithm itself had to change, and there is no
 * second code path that can disagree with the first.
 *
 * <h2>What is deliberately not in here</h2>
 * No network call and no probe of the live model. The point is to detect a <em>configured</em>
 * change; a model silently swapped underneath a fixed configuration is not a failure mode this
 * project can observe, and paying an API call per document to look for it would be absurd.
 */
@Component
public class EmbeddingModelFingerprint {

    private final String value;

    /**
     * Reads the provider, model name and output width from the environment.
     *
     * <p>Every part is read as a plain property rather than from a provider bean: the fingerprint
     * has to be computable before any embedding model exists, and reading the same
     * {@code spring.ai.*} keys the autoconfigurations read means it cannot disagree with what is
     * actually wired.
     *
     * @param environment the resolved application environment
     */
    public EmbeddingModelFingerprint(Environment environment) {
        String provider = EmbeddingProviderEnvironmentPostProcessor.GOOGLE_GENAI_PROVIDER.equals(
                environment.getProperty(EmbeddingProviderEnvironmentPostProcessor.TEXT_EMBEDDING_SELECTOR_PROPERTY))
                        ? EmbeddingProviderEnvironmentPostProcessor.GOOGLE_GENAI_PROVIDER
                        : EmbeddingProviderEnvironmentPostProcessor.OLLAMA_PROVIDER;

        this.value = compose(provider, environment);
    }

    /**
     * Direct construction, for callers that are not a Spring context — the unit tests, and
     * anything that wants to assert a specific fingerprint rather than the configured one.
     *
     * @param provider   the selected provider, as it appears in the selector property
     * @param modelName  the configured model name; blank becomes {@code unknown}
     * @param dimensions the configured output width, or {@code 0} when unset
     */
    public EmbeddingModelFingerprint(String provider, String modelName, int dimensions) {
        this.value = provider + ":" + (hasText(modelName) ? modelName.trim() : "unknown") + ":" + dimensions;
    }

    private static String compose(String provider, Environment environment) {
        String modelName = EmbeddingProviderEnvironmentPostProcessor.GOOGLE_GENAI_PROVIDER.equals(provider)
                ? environment.getProperty("spring.ai.google.genai.embedding.text.model")
                : environment.getProperty("spring.ai.ollama.embedding.options.model");

        // Dimensions are read from the store's own configuration rather than from the provider's
        // default, because the store's value is the one the schema was built for: a mismatch there
        // is an insert-time failure, and matching it here means the fingerprint changes in the same
        // commit that would otherwise break every write.
        String dimensions = environment.getProperty("spring.ai.vectorstore.pgvector.dimensions", "0");

        return new EmbeddingModelFingerprint(provider, modelName, parse(dimensions)).value;
    }

    /**
     * The fingerprint mixed into {@code contentHash}: provider, model name and output width.
     *
     * @return a value that changes if and only if the configured embedding model does
     */
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    private static int parse(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}