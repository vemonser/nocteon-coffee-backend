package com.nocteon.nocteon_api.ai.config;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * Chooses which provider supplies the embedding model, and switches the vector index off when no
 * provider is configured at all.
 *
 * <h2>The rule</h2>
 * <ol>
 * <li>{@code AI_EMBEDDING_PROVIDER} wins when it is set — {@code gemini},
 * {@code openai-compatible}, {@code ollama} or {@code none}. It is the escape hatch, and it is the
 * only way to reach Ollama.</li>
 * <li>Otherwise {@code GEMINI_API_KEY} selects Gemini. That is the preferred hosted provider: its
 * key is the one this project actually holds, and an out-of-quota key is a warning at index time,
 * not a reason to silently embed somewhere else.</li>
 * <li>Otherwise {@code EMBEDDING_API_KEY} selects the generic OpenAI-compatible provider. It is
 * second only because Gemini is the vendor whose key is already deployed; a machine that holds
 * neither key is not going to hold an OpenAI-compatible one either, so the order costs nothing.</li>
 * <li>Otherwise <strong>no provider</strong>: keyword-only search, no vector index, no local
 * Ollama attempt. See {@link #NO_PROVIDER}.</li>
 * </ol>
 *
 * <h2>Why Ollama is never the fallback</h2>
 * A silent fallback to a local model server is not a fallback, it is a hidden requirement: on the
 * deployment host there is no Ollama, so the index build fails at every boot and semantic search
 * is quietly dead while the logs claim a model was configured. Keyword-only is the honest
 * degraded mode — it needs no process, no image and no GPU — and it is now the no-key default.
 * Ollama remains fully available, but only when somebody types
 * {@code AI_EMBEDDING_PROVIDER=ollama}.
 *
 * <h2>Why Groq is not an option here</h2>
 * Groq has no embeddings endpoint. Its OpenAI-compatible API serves chat completions only, so it
 * can host the assistant and cannot host the index. That asymmetry is the whole reason chat and
 * embeddings are two independent decisions rather than one.
 *
 * <h2>Why this is a post-processor and not a flag</h2>
 * Spring AI 2.0.1 gates each provider's embedding autoconfiguration on a selector property with
 * {@code matchIfMissing = true} — verified against the bytecode of
 * {@code OllamaEmbeddingAutoConfiguration}, {@code OpenAiEmbeddingAutoConfiguration} and
 * {@code GoogleGenAiTextEmbeddingAutoConfiguration}. With every starter on the classpath, an unset
 * selector means several autoconfigurations claim the one {@code EmbeddingModel}, and
 * {@code PgVectorStore} — which is constructed with it — makes that a boot failure. And the
 * decision is conditional ("use Gemini if that key is present"), which a YAML placeholder cannot
 * express and {@code @ConditionalOnProperty} is evaluated too late to compute.
 *
 * <h2>Two selector properties, and that is not a mistake</h2>
 * The Gemini selector is {@code spring.ai.model.embedding.<em>text</em>}; Ollama and OpenAI test
 * the shorter {@code spring.ai.model.embedding}. Verified in the 2.0.1 jars. So this class writes
 * two properties, not one: the selected provider's selector is switched on and the rejected
 * providers' selectors are switched <em>off</em> — necessarily, because each of them is
 * {@code matchIfMissing = true}, so leaving one unset is exactly the ambiguity described above.
 *
 * <h2>Why the vector store is switched off on the no-provider path</h2>
 * {@code PgVectorStoreAutoConfiguration#vectorStore} takes an {@code EmbeddingModel} parameter, so
 * with no embedding provider there is no store that can be built. Its own gate is
 * {@code spring.ai.vectorstore.type} with {@code matchIfMissing = true}, so this class writes
 * {@code none} — a value no {@code havingValue} matches — and the store simply does not exist.
 * The services that use it take it as an optional dependency and degrade to keyword search, which
 * is the same outcome as a store that exists but is empty, without a fake bean or a failing insert.
 *
 * <h2>Why the Gemini connection autoconfiguration is excluded by name</h2>
 * {@code GoogleGenAiEmbeddingConnectionAutoConfiguration} is the one piece of the Gemini starter no
 * selector governs: its only conditions are {@code @ConditionalOnClass} and
 * {@code @ConditionalOnMissingBean}, so {@code spring.ai.model.embedding.text=none} switches off the
 * <em>model</em> and leaves the <em>connection</em> to be built anyway — which, with no key, falls
 * through to the Vertex AI branch and fails on
 * {@code Assert.hasText(projectId, "Google GenAI project-id must be set!")}. It therefore has to be
 * excluded explicitly whenever Gemini is not the selected provider.
 *
 * <h2>Why an explicit selector still wins</h2>
 * Setting {@code spring.ai.model.embedding} or {@code spring.ai.model.embedding.text} directly takes
 * precedence over the derivation: the pinned value is never overwritten, only the <em>other</em>
 * selector is resolved so that exactly one provider claims the bean. That is what lets a machine
 * with a Gemini key in its environment still run one directory against a different store.
 */
public class EmbeddingProviderEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /**
     * The Spring AI selector the Ollama and OpenAI embedding autoconfigurations test.
     *
     * <p>The exact name matters: writing it to the wrong key would leave both providers claiming
     * the {@code EmbeddingModel}.
     */
    public static final String EMBEDDING_SELECTOR_PROPERTY = "spring.ai.model.embedding";

    /**
     * The Spring AI selector the Google GenAI embedding autoconfiguration tests.
     *
     * <p>Separate from {@link #EMBEDDING_SELECTOR_PROPERTY} by Spring AI's own design — see the
     * class javadoc. Both are written on every run.
     */
    public static final String TEXT_EMBEDDING_SELECTOR_PROPERTY = "spring.ai.model.embedding.text";

    /**
     * The Spring AI vector-store selector.
     *
     * <p>Gated with {@code matchIfMissing = true} too, which is why the no-provider path has to
     * write {@link #NO_PROVIDER} here rather than leave it alone.
     */
    public static final String VECTOR_STORE_TYPE_PROPERTY = "spring.ai.vectorstore.type";

    /** The selector value that activates {@code GoogleGenAiTextEmbeddingAutoConfiguration}. */
    public static final String GOOGLE_GENAI_PROVIDER = "google-genai";

    /** The selector value that activates {@code OpenAiEmbeddingAutoConfiguration}. */
    public static final String OPENAI_PROVIDER = "openai";

    /** The selector value that activates {@code OllamaEmbeddingAutoConfiguration}. */
    public static final String OLLAMA_PROVIDER = "ollama";

    /**
     * No provider, and therefore no vector index.
     *
     * <p>Not a Spring AI concept — it is what "this provider is not selected" looks like to a
     * {@code @ConditionalOnProperty} with {@code havingValue}. Every selector is
     * {@code matchIfMissing = true}, so the only way to turn a provider off is to give its selector
     * a value it does not want; this project already uses {@code none} for the unused image and
     * moderation models, so the same word is reused here rather than inventing a second one.
     *
     * <p>As a decision it means "keyword-only": no {@code EmbeddingModel}, no {@code VectorStore},
     * no call to any provider. It is the default when no key is configured.
     */
    public static final String NO_PROVIDER = "none";

    /**
     * Where the resolved decision is published, so nothing has to re-derive it.
     *
     * <p>{@link EmbeddingModelFingerprint} mixes it into every document hash and
     * {@code ProductIndexBootstrap} logs it, and both need the decision this class made rather than
     * a second reading of the environment that could disagree with the wiring.
     */
    public static final String PROVIDER_DECISION_PROPERTY = "nocteon.ai.embedding-provider";

    /**
     * The one Gemini autoconfiguration no selector reaches.
     *
     * <p>See the class javadoc: it is not gated by {@code spring.ai.model.embedding.text}, so it has
     * to be excluded by name or a blank API key becomes a startup failure.
     */
    public static final String GOOGLE_GENAI_CONNECTION_AUTOCONFIGURATION =
            "org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionAutoConfiguration";

    /**
     * Spring Boot's standard exclusion list.
     *
     * <p>Written to rather than accumulating onto: this is the only writer in this application, and
     * {@code addFirst} would shadow any value a test or a future operator put there anyway.
     */
    private static final String AUTOCONFIGURE_EXCLUDE_PROPERTY = "spring.autoconfigure.exclude";

    /** Property source name, exposed so it can be removed or inspected in tests. */
    public static final String PROPERTY_SOURCE_NAME = "nocteon-ai-embedding-provider";

    /** Gemini's key, through the AI Studio API — not the Vertex AI one. */
    static final String GEMINI_API_KEY = "GEMINI_API_KEY";

    /** The generic OpenAI-compatible provider's key. Its presence alone opts into that provider. */
    static final String EMBEDDING_API_KEY = "EMBEDDING_API_KEY";

    /** Its endpoint; defaults to OpenAI's own in {@code application.yaml}. */
    static final String EMBEDDING_BASE_URL = "EMBEDDING_BASE_URL";

    /** Its model id; defaults to {@code text-embedding-3-small} in {@code application.yaml}. */
    static final String EMBEDDING_MODEL = "EMBEDDING_MODEL";

    /**
     * The operator-facing switch, and the only route to Ollama.
     *
     * <p>Accepted values are applied case-insensitively; see {@link #normalise(String)} for the
     * aliases. An unrecognised value fails startup rather than landing on a provider the operator
     * did not ask for.
     */
    public static final String AI_EMBEDDING_PROVIDER_PROPERTY = "AI_EMBEDDING_PROVIDER";

    /** Alias kept for callers/tests that use the bare variable name as the constant. */
    public static final String AI_EMBEDDING_PROVIDER = "AI_EMBEDDING_PROVIDER";

    /** Alias kept for callers/tests that name the env var after the provider. */
    public static final String EMBEDDING_PROVIDER_PROPERTY = "AI_EMBEDDING_PROVIDER";

    /**
     * {@code LOWEST_PRECEDENCE} for the same reason as
     * {@link ChatProviderEnvironmentPostProcessor}: it must run after
     * {@code ConfigDataEnvironmentPostProcessor}, which is what makes {@code application.yaml}
     * and {@code .env} visible on the {@link ConfigurableEnvironment} by the time the decision is
     * made — and so an explicit override there is seen rather than overwritten.
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String embeddingSelector = trimToNull(environment.getProperty(EMBEDDING_SELECTOR_PROPERTY));
        String textSelector = trimToNull(environment.getProperty(TEXT_EMBEDDING_SELECTOR_PROPERTY));
        String requested = trimToNull(environment.getProperty(AI_EMBEDDING_PROVIDER_PROPERTY));

        String provider = StringUtils.hasText(embeddingSelector) || StringUtils.hasText(textSelector)
                ? providerFromPinnedSelectors(embeddingSelector, textSelector)
                : providerFromEnvironment(requested, environment);

        Map<String, Object> decisions = new LinkedHashMap<>();

        // Resolved per selector, because they are independent properties and either may be pinned
        // on its own. Resolving them as one decision would mean that pinning only the Gemini
        // selector left the Ollama/OpenAI one unset — and an unset selector is "this provider too",
        // not "no provider", so OpenAI would claim the EmbeddingModel and fail on a missing
        // credential. Writing only the unpinned selector keeps an explicit override intact while
        // still leaving exactly one provider claiming the bean.
        if (!StringUtils.hasText(embeddingSelector)) {
            decisions.put(EMBEDDING_SELECTOR_PROPERTY, embeddingSelectorFor(provider));
        }
        if (!StringUtils.hasText(textSelector)) {
            decisions.put(TEXT_EMBEDDING_SELECTOR_PROPERTY,
                    GOOGLE_GENAI_PROVIDER.equals(provider) ? GOOGLE_GENAI_PROVIDER : NO_PROVIDER);
        }

        if (NO_PROVIDER.equals(provider)) {
            // No EmbeddingModel exists, so PgVectorStore could not be constructed even if its own
            // selector asked for it. Switching the store off is what turns "no provider" into
            // keyword-only search instead of a boot failure.
            decisions.put(VECTOR_STORE_TYPE_PROPERTY, NO_PROVIDER);
        }

        if (!GOOGLE_GENAI_PROVIDER.equals(provider)) {
            // The connection autoconfiguration is not selector-gated, so whenever Gemini is not the
            // selected provider it has to be removed rather than selected away — otherwise a blank
            // key is a boot failure. Applied on the pinned path too, because pinning
            // `embedding.text=none` without a key is the same situation.
            decisions.put(AUTOCONFIGURE_EXCLUDE_PROPERTY, GOOGLE_GENAI_CONNECTION_AUTOCONFIGURATION);
        }

        decisions.put(PROVIDER_DECISION_PROPERTY, provider);

        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, decisions));
    }

    /**
     * The selector value the Ollama/OpenAI autoconfigurations are given for a resolved provider.
     *
     * <p>{@link #NO_PROVIDER} for anything that is not Ollama or OpenAI, including Gemini: the
     * Gemini model is activated by the other selector, and leaving this one unset would also
     * activate Ollama and OpenAI.
     */
    private static String embeddingSelectorFor(String provider) {
        if (OLLAMA_PROVIDER.equals(provider) || OPENAI_PROVIDER.equals(provider)) {
            return provider;
        }
        return NO_PROVIDER;
    }

    /**
     * The provider a pinned selector pair names, for the decision property only.
     *
     * <p>Nothing is overwritten on this path, so the return value feeds the fingerprint and the log
     * line rather than the wiring. A selector naming a provider this project does not ship is
     * passed through unchanged: it is the operator's value, and guessing a different one would
     * describe a model that is not in use.
     */
    private static String providerFromPinnedSelectors(String embeddingSelector, String textSelector) {
        if (GOOGLE_GENAI_PROVIDER.equals(textSelector)) {
            return GOOGLE_GENAI_PROVIDER;
        }
        if (OLLAMA_PROVIDER.equals(embeddingSelector)) {
            return OLLAMA_PROVIDER;
        }
        if (OPENAI_PROVIDER.equals(embeddingSelector)) {
            return OPENAI_PROVIDER;
        }
        if (StringUtils.hasText(textSelector) && !NO_PROVIDER.equals(textSelector)) {
            return textSelector;
        }
        return NO_PROVIDER;
    }

    /**
     * The provider the environment asks for, when no selector is pinned.
     *
     * <p>Order is the contract: an explicit switch, then Gemini's key, then a generic hosted key,
     * then nothing. Never Ollama — that is only reachable through the explicit switch.
     */
    private static String providerFromEnvironment(String requested, ConfigurableEnvironment environment) {
        if (StringUtils.hasText(requested)) {
            String provider = normalise(requested);
            if (OPENAI_PROVIDER.equals(provider)) {
                requireEmbeddingApiKey(requested, environment);
            }
            if (GOOGLE_GENAI_PROVIDER.equals(provider)) {
                requireGeminiApiKey(requested, environment);
            }
            return provider;
        }

        if (StringUtils.hasText(environment.getProperty(GEMINI_API_KEY))) {
            return GOOGLE_GENAI_PROVIDER;
        }

        if (StringUtils.hasText(environment.getProperty(EMBEDDING_API_KEY))) {
            return OPENAI_PROVIDER;
        }

        return NO_PROVIDER;
    }

    /**
     * Maps an {@code AI_EMBEDDING_PROVIDER} value onto the selector vocabulary.
     *
     * <p>Fails startup on an unrecognised value. A typo that silently resolved to a different
     * provider would be discovered from the index contents, months later and only if somebody
     * looked — which is the class of failure this whole design exists to remove.
     *
     * @param requested the operator's value, never blank
     * @return {@code google-genai}, {@code openai}, {@code ollama} or {@code none}
     */
    static String normalise(String requested) {
        String value = requested.trim().toLowerCase(Locale.ROOT);

        return switch (value) {
            case "gemini", "google-genai", "google", "google-ai" -> GOOGLE_GENAI_PROVIDER;
            case "openai-compatible", "openai", "openai-compatible-embeddings" -> OPENAI_PROVIDER;
            case "ollama", "local" -> OLLAMA_PROVIDER;
            case "none", "off", "false", "keyword", "keyword-only" -> NO_PROVIDER;
            default -> throw new IllegalStateException(
                    AI_EMBEDDING_PROVIDER_PROPERTY + " is set to '" + requested + "', which is not a "
                            + "provider this project can wire. Use one of: gemini (a GEMINI_API_KEY), "
                            + "openai-compatible (EMBEDDING_BASE_URL + EMBEDDING_API_KEY + EMBEDDING_MODEL), "
                            + "ollama (a local Ollama serving an embedding model), or none (keyword-only "
                            + "search, no vector index). Leave it unset to let the keys decide.");
        };
    }

    /**
     * Fails startup when the generic provider is asked for without the credential it cannot work
     * without.
     *
     * <p>Only the key is mandatory: {@code EMBEDDING_BASE_URL} and {@code EMBEDDING_MODEL} default to
     * OpenAI's own values in {@code application.yaml}, which is a legitimate target for this switch.
     * Any other vendor needs all three, and the message says so.
     */
    private static void requireEmbeddingApiKey(String requested, ConfigurableEnvironment environment) {
        if (!StringUtils.hasText(environment.getProperty(EMBEDDING_API_KEY))) {
            throw new IllegalStateException(
                    AI_EMBEDDING_PROVIDER_PROPERTY + "=" + requested + " needs " + EMBEDDING_API_KEY
                            + " to be set, or there is nothing to authenticate against. For a vendor other "
                            + "than OpenAI also set " + EMBEDDING_BASE_URL + " (its API root, ending in the "
                            + "OpenAI-compatible version segment) and " + EMBEDDING_MODEL + " (the embedding "
                            + "model id). To run without a hosted provider, set "
                            + AI_EMBEDDING_PROVIDER_PROPERTY + "=none and search stays keyword-only.");
        }
    }

    /**
     * Fails startup when Gemini is asked for by name without its key.
     *
     * <p>The alternative is the Gemini starter's own failure — "Google GenAI project-id must be
     * set!" from the Vertex AI fallback — which describes a different provider's configuration and
     * sends the reader looking for a Cloud project id they do not need. A missing key is an
     * operator mistake, so it gets an operator message.
     *
     * <p>Only the {@code AI_EMBEDDING_PROVIDER} path is validated. Pinning the raw Spring selector
     * is a deliberate bypass of every decision in this class, and is left to Spring.
     */
    private static void requireGeminiApiKey(String requested, ConfigurableEnvironment environment) {
        if (!StringUtils.hasText(environment.getProperty(GEMINI_API_KEY))) {
            throw new IllegalStateException(
                    AI_EMBEDDING_PROVIDER_PROPERTY + "=" + requested + " needs " + GEMINI_API_KEY
                            + " to be set. Gemini embeddings go through the AI Studio API with a plain API "
                            + "key — not the Vertex AI path, which needs a billing-enabled Cloud project. "
                            + "Set the key, or leave " + AI_EMBEDDING_PROVIDER_PROPERTY + " unset so the key "
                            + "itself decides, or set it to none for keyword-only search.");
        }
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
