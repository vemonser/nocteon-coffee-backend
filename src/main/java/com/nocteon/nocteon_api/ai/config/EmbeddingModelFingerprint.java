package com.nocteon.nocteon_api.ai.config;

import java.net.URI;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
 * <h2>What the identity is</h2>
 * {@code <provider>:<model>:<requested>@<column>}, where
 * <ul>
 * <li><b>provider</b> is the wiring decision — {@code google-genai}, {@code openai},
 * {@code ollama} or {@code none}. Two providers with the same model name are still two embedding
 * spaces;</li>
 * <li><b>model</b> is the configured model id. For the generic OpenAI-compatible provider the id
 * carries the endpoint host ({@code text-embedding-3-small@api.openai.com}): the same model id at
 * two vendors is two services, and pointing {@code EMBEDDING_BASE_URL} somewhere else must
 * re-embed rather than blend two vendors' vectors in one table;</li>
 * <li><b>requested</b> is the output width the model is asked for, and <b>column</b> is the width
 * of {@code vector_store}'s column. Both are in the fingerprint because they fail differently: a
 * request-width change moves the vectors into another space, and a column change is the schema the
 * writes have to satisfy. Either one alone is a reason to re-embed.</li>
 * </ul>
 *
 * <h2>What is deliberately not in here</h2>
 * No network call and no probe of the live model, and no API key or URL path — only the endpoint's
 * host, because that is the part that identifies the vendor and none of the part that identifies a
 * secret. The point is to detect a <em>configured</em> change; a model silently swapped underneath
 * a fixed configuration is not a failure mode this project can observe, and paying an API call per
 * document to look for it would be absurd.
 *
 * <h2>Why the environment constructor is annotated</h2>
 * There are two constructors: this one and the direct one below, which exists for callers that
 * are not a Spring context. Spring does not infer between them. With more than one declared
 * constructor and none of them annotated, it declines to choose and falls back to looking for a
 * no-arg constructor, which this class does not have — so the application refuses to boot with
 * "No default constructor found", while every unit test passes because they all call
 * {@code new} directly and never ask Spring to build the bean. {@code @Autowired} says which one
 * the container must use, and that is the whole reason it is here.
 */
@Component
public class EmbeddingModelFingerprint {

    /** The width the schema was built for, as the vector store is configured. */
    private static final String STORE_DIMENSIONS_PROPERTY = "spring.ai.vectorstore.pgvector.dimensions";

    private static final String GEMINI_MODEL_PROPERTY = "spring.ai.google.genai.embedding.text.model";

    private static final String GEMINI_DIMENSIONS_PROPERTY = "spring.ai.google.genai.embedding.text.dimensions";

    private static final String OPENAI_MODEL_PROPERTY = "spring.ai.openai.embedding.model";

    /** The nested form the OpenAI options also accept; read when the flat one is absent. */
    private static final String OPENAI_NESTED_MODEL_PROPERTY = "spring.ai.openai.embedding.options.model";

    private static final String OPENAI_DIMENSIONS_PROPERTY = "spring.ai.openai.embedding.dimensions";

    private static final String OPENAI_EMBEDDING_BASE_URL_PROPERTY = "spring.ai.openai.embedding.base-url";

    /** The endpoint the generic provider would fall back to; only its host is fingerprinted. */
    private static final String OPENAI_BASE_URL_PROPERTY = "spring.ai.openai.base-url";

    private static final String OLLAMA_MODEL_PROPERTY = "spring.ai.ollama.embedding.options.model";

    /** The model id recorded when there is no provider at all — keyword-only search. */
    static final String KEYWORD_ONLY_MODEL = "keyword-only";

    private final String value;

    /**
     * Reads the provider, model name and output widths from the environment.
     *
     * <p>Every part is read as a plain property rather than from a provider bean: the fingerprint
     * has to be computable before any embedding model exists, and reading the same
     * {@code spring.ai.*} keys the autoconfigurations read means it cannot disagree with what is
     * actually wired. The provider comes from the decision
     * {@link EmbeddingProviderEnvironmentPostProcessor} publishes, and only falls back to reading
     * the selectors itself when that property is absent (a context that never ran the
     * post-processor).
     *
     * @param environment the resolved application environment
     */
    @Autowired
    public EmbeddingModelFingerprint(Environment environment) {
        this.value = compose(environment);
    }

    /**
     * Direct construction, for callers that are not a Spring context — the unit tests, and
     * anything that wants to assert a specific fingerprint rather than the configured one.
     *
     * <p>One width for both halves: a caller naming a single dimension has no schema width to
     * distinguish from it, which is the common case when the two agree anyway.
     *
     * @param provider   the selected provider, as it appears in the selector property
     * @param modelName  the configured model name; blank becomes {@code unknown}
     * @param dimensions the configured output width, or {@code 0} when unset
     */
    public EmbeddingModelFingerprint(String provider, String modelName, int dimensions) {
        this.value = compose(provider, modelName, dimensions, dimensions);
    }

    private static String compose(Environment environment) {
        String provider = providerOf(environment);

        int columnWidth = parse(environment.getProperty(STORE_DIMENSIONS_PROPERTY, "0"));

        return switch (provider) {
            case EmbeddingProviderEnvironmentPostProcessor.GOOGLE_GENAI_PROVIDER -> compose(
                    provider,
                    environment.getProperty(GEMINI_MODEL_PROPERTY),
                    parse(environment.getProperty(GEMINI_DIMENSIONS_PROPERTY, String.valueOf(columnWidth))),
                    columnWidth);
            case EmbeddingProviderEnvironmentPostProcessor.OPENAI_PROVIDER -> compose(
                    provider,
                    vendorQualifiedModel(environment),
                    parse(environment.getProperty(OPENAI_DIMENSIONS_PROPERTY, String.valueOf(columnWidth))),
                    columnWidth);
            case EmbeddingProviderEnvironmentPostProcessor.OLLAMA_PROVIDER -> compose(
                    provider,
                    environment.getProperty(OLLAMA_MODEL_PROPERTY),
                    // A local model emits its native width; there is no request parameter to read,
                    // so the column it is written into is the only width this project configures.
                    columnWidth,
                    columnWidth);
            default -> compose(provider, KEYWORD_ONLY_MODEL, 0, columnWidth);
        };
    }

    /**
     * The provider id, from the decision property when it is there.
     *
     * <p>The fallback reads the selectors, and it exists for contexts built without the
     * post-processor — every unit test in this package. It resolves the same three providers; it
     * cannot detect a selector naming something this project does not ship, and returns
     * {@code none} for it, which is the same conservative answer the decision path gives.
     */
    static String providerOf(Environment environment) {
        String decided = environment.getProperty(EmbeddingProviderEnvironmentPostProcessor.PROVIDER_DECISION_PROPERTY);
        if (StringUtils.hasText(decided)) {
            return decided.trim();
        }

        String textSelector = environment.getProperty(
                EmbeddingProviderEnvironmentPostProcessor.TEXT_EMBEDDING_SELECTOR_PROPERTY);
        if (EmbeddingProviderEnvironmentPostProcessor.GOOGLE_GENAI_PROVIDER.equals(textSelector)) {
            return EmbeddingProviderEnvironmentPostProcessor.GOOGLE_GENAI_PROVIDER;
        }

        String embeddingSelector = environment.getProperty(
                EmbeddingProviderEnvironmentPostProcessor.EMBEDDING_SELECTOR_PROPERTY);
        if (EmbeddingProviderEnvironmentPostProcessor.OLLAMA_PROVIDER.equals(embeddingSelector)) {
            return EmbeddingProviderEnvironmentPostProcessor.OLLAMA_PROVIDER;
        }
        if (EmbeddingProviderEnvironmentPostProcessor.OPENAI_PROVIDER.equals(embeddingSelector)) {
            return EmbeddingProviderEnvironmentPostProcessor.OPENAI_PROVIDER;
        }
        return EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER;
    }

    /**
     * The model id decorated with the endpoint's host.
     *
     * <p>Only the host: it is the part that identifies the vendor, while the path and the key are
     * either irrelevant to the embedding space or secret. A blank or unparseable URL degrades to
     * the bare model id rather than throwing — a fingerprint that is slightly less specific is
     * better than a bean that cannot be built.
     */
    private static String vendorQualifiedModel(Environment environment) {
        String model = environment.getProperty(OPENAI_MODEL_PROPERTY);
        if (!StringUtils.hasText(model)) {
            model = environment.getProperty(OPENAI_NESTED_MODEL_PROPERTY);
        }

        String baseUrl = environment.getProperty(OPENAI_EMBEDDING_BASE_URL_PROPERTY);
        if (!StringUtils.hasText(baseUrl)) {
            baseUrl = environment.getProperty(OPENAI_BASE_URL_PROPERTY);
        }

        String host = hostOf(baseUrl);
        return host == null ? model : (StringUtils.hasText(model) ? model.trim() : "unknown") + "@" + host;
    }

    private static String hostOf(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            String host = URI.create(url.trim()).getHost();
            return StringUtils.hasText(host) ? host : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String compose(String provider, String modelName, int requestedWidth, int columnWidth) {
        return provider + ":" + (StringUtils.hasText(modelName) ? modelName.trim() : "unknown")
                + ":" + requestedWidth + "@" + columnWidth;
    }

    /**
     * The fingerprint mixed into {@code contentHash}: provider, model id and both widths.
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
}
