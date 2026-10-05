package com.nocteon.nocteon_api.ai.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * Chooses which provider supplies the embedding model, from whether a Gemini API key is
 * configured.
 *
 * <h2>Why this is a second post-processor and not a flag</h2>
 * It is deliberately the same mechanism as {@link ChatProviderEnvironmentPostProcessor}, for the
 * same reason: Spring AI 2.0.1 gates each provider's embedding autoconfiguration on a selector
 * property with {@code matchIfMissing = true} — verified against the bytecode of
 * {@code OllamaEmbeddingAutoConfiguration},
 * {@code OpenAiEmbeddingAutoConfiguration} and
 * {@code GoogleGenAiTextEmbeddingAutoConfiguration}, which carry
 * {@code @ConditionalOnProperty(name = "spring.ai.model.embedding", havingValue = "<provider>",
 * matchIfMissing = true)} for the first two and
 * {@code @ConditionalOnProperty(name = "spring.ai.model.embedding.text", havingValue =
 * "google-genai", matchIfMissing = true)} for the third. Only one {@link
 * org.springframework.ai.embedding.EmbeddingModel} may exist, because
 * {@code PgVectorStore} is constructed with it and an ambiguous one is a boot failure.
 *
 * <p>Reusing the mechanism rather than inventing a second one is also what keeps the two
 * decisions readable side by side: "which chat model" and "which embedding model" are the same
 * question asked of the same framework, and a reader should be able to answer both by reading
 * two short classes.
 *
 * <h2>Two selector properties, and that is not a mistake</h2>
 * The Gemini selector is {@code spring.ai.model.embedding.<em>text</em>}, not
 * {@code spring.ai.model.embedding}. Verified in the 2.0.1 jar: the Ollama and OpenAI
 * autoconfigurations test the shorter name with {@code havingValue = "ollama"} /
 * {@code "openai"}, while the Google GenAI one tests
 * {@code spring.ai.model.embedding.text} with {@code havingValue = "google-genai"}. So this
 * class writes two properties, not one: the selected provider's selector is switched on and the
 * rejected providers' selectors are switched <em>off</em>. Switching them off is not optional —
 * every one of them is {@code matchIfMissing = true}, so leaving the others unset is exactly the
 * ambiguity described above.
 *
 * <h2>Why the Gemini connection autoconfiguration is excluded on the local path</h2>
 * {@code GoogleGenAiEmbeddingConnectionAutoConfiguration} is the one piece of the Gemini starter
 * that no selector governs. Verified in the 2.0.1 jar: its only conditions are
 * {@code @ConditionalOnClass} and {@code @ConditionalOnMissingBean}, so
 * {@code spring.ai.model.embedding.text=none} switches off the <em>model</em> but leaves the
 * <em>connection</em> bean to be created anyway. With no API key it falls through to the Vertex AI
 * branch and fails on {@code Assert.hasText(projectId, "Google GenAI project-id must be set!")} —
 * so adding the starter to the classpath would make the application refuse to start on a machine
 * with no {@code GEMINI_API_KEY}, which is precisely the zero-configuration path this fallback
 * exists to protect. It therefore has to be excluded explicitly rather than selected away.
 *
 * <p>Still a property value and not a try/catch — the same design as the chat selector, just
 * covering the one autoconfiguration the selector cannot reach.
 *
 * <h2>Why an explicit override still wins</h2>
 * Setting {@code spring.ai.model.embedding} or {@code spring.ai.model.embedding.text} directly
 * takes precedence over the derivation, exactly as for chat. That is what lets a machine that
 * happens to have a Gemini key in its environment still run the demo against the local model.
 *
 * <p>Note that honouring an explicit override means this class installs <em>nothing</em> in that
 * case, so the operator's value is the only one present — the selectors this class would have
 * written are deliberately not added on top, which would make the override a no-op.
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

    /** The selector value that activates {@code GoogleGenAiTextEmbeddingAutoConfiguration}. */
    public static final String GOOGLE_GENAI_PROVIDER = "google-genai";

    /** The selector value that activates {@code OllamaEmbeddingAutoConfiguration}. */
    public static final String OLLAMA_PROVIDER = "ollama";

    /**
     * A selector value that matches no provider.
     *
     * <p>Not a Spring AI concept — it is what "this provider is not selected" looks like to a
     * {@code @ConditionalOnProperty} with {@code havingValue}. Every selector is
     * {@code matchIfMissing = true}, so the only way to turn a provider off is to give its
     * selector a value it does not want; this project already uses {@code none} for the unused
     * image and moderation models, so the same word is reused here rather than inventing a second
     * one.
     */
    public static final String NO_PROVIDER = "none";

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

    private static final String GEMINI_API_KEY = "GEMINI_API_KEY";

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
        Map<String, Object> decisions = new LinkedHashMap<>();

        // Resolved per selector, because they are independent properties and either may be pinned
        // on its own. Resolving them as one decision would mean that pinning only the Gemini
        // selector left the Ollama/OpenAI one unset — and an unset selector is "this provider too",
        // not "no provider", so OpenAI would claim the EmbeddingModel and fail on a missing
        // credential. Writing only the unpinned selector keeps an explicit override intact while
        // still leaving exactly one provider claiming the bean.
        String embeddingSelector = environment.getProperty(EMBEDDING_SELECTOR_PROPERTY);
        String textSelector = environment.getProperty(TEXT_EMBEDDING_SELECTOR_PROPERTY);

        boolean gemini = StringUtils.hasText(textSelector)
                ? GOOGLE_GENAI_PROVIDER.equals(textSelector)
                : hasGeminiApiKey(environment);

        if (!StringUtils.hasText(embeddingSelector)) {
            decisions.put(EMBEDDING_SELECTOR_PROPERTY, gemini ? NO_PROVIDER : OLLAMA_PROVIDER);
        }
        if (!StringUtils.hasText(textSelector)) {
            decisions.put(TEXT_EMBEDDING_SELECTOR_PROPERTY, gemini ? GOOGLE_GENAI_PROVIDER : NO_PROVIDER);
        }

        if (!gemini) {
            // The connection autoconfiguration is not selector-gated, so whenever Gemini is not the
            // selected provider it has to be removed rather than selected away — otherwise a blank
            // key is a boot failure. Applied on the pinned path too, because pinning
            // `embedding.text=none` without a key is the same situation.
            decisions.put(AUTOCONFIGURE_EXCLUDE_PROPERTY, GOOGLE_GENAI_CONNECTION_AUTOCONFIGURATION);
        }

        if (decisions.isEmpty()) {
            return;
        }

        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, decisions));
    }

    

    /**
     * Whether a Gemini key is configured.
     *
     * <p>Read as {@code GEMINI_API_KEY} rather than {@code spring.ai.google.genai.embedding.api-key}
     * for the same reason chat reads {@code GROQ_API_KEY}: the env var is the thing an operator
     * sets, and the YAML placeholder that reads it has not necessarily resolved yet at this point
     * in the lifecycle.
     *
     * <p>Blank and whitespace-only both count as absent. An unset variable in a {@code .env} file
     * reads as an empty string, and treating that as "configured" would point the embedding model
     * at a provider it cannot authenticate against — which, unlike a chat 401, would surface as
     * every reindex silently failing.
     */
    private static boolean hasGeminiApiKey(ConfigurableEnvironment environment) {
        return StringUtils.hasText(environment.getProperty(GEMINI_API_KEY));
    }
}