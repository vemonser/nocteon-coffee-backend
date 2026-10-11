package com.nocteon.nocteon_api.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

/**
 * The embedding provider is chosen from whether {@code GEMINI_API_KEY} is set, and this is the only
 * place that decision is made.
 *
 * <p>It has to live in an {@code EnvironmentPostProcessor} because Spring AI 2.0.1 gates each
 * provider's embedding autoconfiguration on a selector with {@code matchIfMissing = true}, so with
 * several starters on the classpath an unset selector means several autoconfigurations all claim
 * the one {@code EmbeddingModel} and injection by type fails. A YAML placeholder cannot express
 * "only when a key is present", and {@code @ConditionalOnProperty} is evaluated too late to compute
 * anything.
 *
 * <p>These tests assert the decision, not the wiring: the resulting bean types are covered by
 * {@code ChatProviderWiringTest}, which builds an actual context.
 */
@SuppressWarnings("unchecked") // the MapPropertySource this class installs holds Map<String, Object> by construction
class EmbeddingProviderEnvironmentPostProcessorTest {

    private final EmbeddingProviderEnvironmentPostProcessor processor =
            new EmbeddingProviderEnvironmentPostProcessor();

    // --- no key: the local model --------------------------------------------------------

    @Test
    void usesTheLocalModelWhenNoApiKeyIsConfigured() {
        MockEnvironment environment = new MockEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(EmbeddingProviderEnvironmentPostProcessor.EMBEDDING_SELECTOR_PROPERTY))
                .isEqualTo(EmbeddingProviderEnvironmentPostProcessor.OLLAMA_PROVIDER);
        assertThat(environment.getProperty(
                EmbeddingProviderEnvironmentPostProcessor.TEXT_EMBEDDING_SELECTOR_PROPERTY))
                .isEqualTo(EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER);
    }

    /**
     * An unset variable in a {@code .env} file reads as an empty string, not as absent. Treating
     * that as "configured" would point the embedding model at a provider it cannot authenticate
     * against — and unlike a chat 401, that surfaces as every reindex silently failing.
     */
    @Test
    void treatsABlankApiKeyAsAbsent() {
        MockEnvironment environment = new MockEnvironment().withProperty("GEMINI_API_KEY", "");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(EmbeddingProviderEnvironmentPostProcessor.EMBEDDING_SELECTOR_PROPERTY))
                .isEqualTo(EmbeddingProviderEnvironmentPostProcessor.OLLAMA_PROVIDER);
    }

    /** A key of nothing but whitespace is the same mistake, not a key. */
    @Test
    void treatsAWhitespaceOnlyApiKeyAsAbsent() {
        MockEnvironment environment = new MockEnvironment().withProperty("GEMINI_API_KEY", "   ");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(EmbeddingProviderEnvironmentPostProcessor.EMBEDDING_SELECTOR_PROPERTY))
                .isEqualTo(EmbeddingProviderEnvironmentPostProcessor.OLLAMA_PROVIDER);
    }

    // --- key present: Gemini -----------------------------------------------------------

    @Test
    void usesGeminiWhenAnApiKeyIsConfigured() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GEMINI_API_KEY", "not-a-real-key");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(
                EmbeddingProviderEnvironmentPostProcessor.TEXT_EMBEDDING_SELECTOR_PROPERTY))
                .isEqualTo(EmbeddingProviderEnvironmentPostProcessor.GOOGLE_GENAI_PROVIDER);
        assertThat(environment.getProperty(EmbeddingProviderEnvironmentPostProcessor.EMBEDDING_SELECTOR_PROPERTY))
                .isEqualTo(EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER);
    }

    /**
     * Every selector is {@code matchIfMissing = true}, so the rejected provider has to be switched
     * off explicitly. Leaving the Ollama selector unset while switching on Gemini is not a
     * tie-break — it is two autoconfigurations both defining an {@code EmbeddingModel}, which is the
     * boot failure this class exists to prevent.
     */
    @Test
    void switchesOffEveryRejectedProvider() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GEMINI_API_KEY", "not-a-real-key");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(EmbeddingProviderEnvironmentPostProcessor.EMBEDDING_SELECTOR_PROPERTY))
                .isNotEqualTo(EmbeddingProviderEnvironmentPostProcessor.OLLAMA_PROVIDER);
    }

    /** And the mirror image: Gemini is switched off on the local path. */
    @Test
    void switchesOffGeminiWhenTheLocalModelIsSelected() {
        MockEnvironment environment = new MockEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(
                EmbeddingProviderEnvironmentPostProcessor.TEXT_EMBEDDING_SELECTOR_PROPERTY))
                .isNotEqualTo(EmbeddingProviderEnvironmentPostProcessor.GOOGLE_GENAI_PROVIDER);
    }

    // --- explicit override --------------------------------------------------------------

    /**
     * An operator pinning the provider wins over the derivation, on either selector. This is what
     * lets a machine that has a key in its environment still run the demo against the local model.
     */
    @Test
    void anExplicitOllamaSelectorOverridesTheDerivedValue() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GEMINI_API_KEY", "not-a-real-key")
                .withProperty(EmbeddingProviderEnvironmentPostProcessor.EMBEDDING_SELECTOR_PROPERTY, "ollama");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(EmbeddingProviderEnvironmentPostProcessor.EMBEDDING_SELECTOR_PROPERTY))
                .isEqualTo(EmbeddingProviderEnvironmentPostProcessor.OLLAMA_PROVIDER);
    }

    @Test
    void anExplicitTextSelectorOverridesTheDerivedValue() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GEMINI_API_KEY", "not-a-real-key")
                .withProperty(EmbeddingProviderEnvironmentPostProcessor.TEXT_EMBEDDING_SELECTOR_PROPERTY, "none");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(
                EmbeddingProviderEnvironmentPostProcessor.TEXT_EMBEDDING_SELECTOR_PROPERTY))
                .isEqualTo("none");
    }

    /**
     * With nothing pinned and a key present, the post-processor has both selectors to decide and so
     * installs its source.
     */
    @Test
    void installsAPropertySourceWhenItHasADecisionToMake() {
        MockEnvironment environment = new MockEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getPropertySources()
                .contains(EmbeddingProviderEnvironmentPostProcessor.PROPERTY_SOURCE_NAME))
                .isTrue();
    }

    /**
     * The pinned value survives untouched, and the Gemini connection autoconfiguration is removed
     * with it.
     *
     * <p>The pinned selector is a <em>local</em> one here, so Gemini is not the selected provider
     * and the un-gated connection autoconfiguration has to go. Pinning a provider cannot leave the
     * application in a state it would not have reached on its own.
     */
    @Test
    void pinningTheLocalModelAlsoRemovesTheGeminiConnection() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GEMINI_API_KEY", "not-a-real-key")
                .withProperty(EmbeddingProviderEnvironmentPostProcessor.EMBEDDING_SELECTOR_PROPERTY, "ollama")
                .withProperty(EmbeddingProviderEnvironmentPostProcessor.TEXT_EMBEDDING_SELECTOR_PROPERTY, "none");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(EmbeddingProviderEnvironmentPostProcessor.EMBEDDING_SELECTOR_PROPERTY))
                .isEqualTo("ollama");
        assertThat(environment.getProperty(
                EmbeddingProviderEnvironmentPostProcessor.TEXT_EMBEDDING_SELECTOR_PROPERTY))
                .isEqualTo("none");
        assertThat(environment.getProperty("spring.autoconfigure.exclude"))
                .isEqualTo(EmbeddingProviderEnvironmentPostProcessor.GOOGLE_GENAI_CONNECTION_AUTOCONFIGURATION);
    }

    /**
     * Pinning Gemini must leave the connection autoconfiguration in place, or the provider the
     * operator selected would have no connection to build.
     */
    @Test
    void pinningGeminiKeepsTheConnection() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(EmbeddingProviderEnvironmentPostProcessor.TEXT_EMBEDDING_SELECTOR_PROPERTY, "google-genai");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.autoconfigure.exclude")).isNull();
    }

    /**
     * A pinned Gemini selector wins over the key. This is the case an operator uses to force the
     * hosted model on a machine whose environment has no key at all — and it must not be undone by
     * the absence of one.
     */
    @Test
    void pinningGeminiSelectsGeminiEvenWithoutAKey() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(EmbeddingProviderEnvironmentPostProcessor.TEXT_EMBEDDING_SELECTOR_PROPERTY, "google-genai");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(
                EmbeddingProviderEnvironmentPostProcessor.TEXT_EMBEDDING_SELECTOR_PROPERTY))
                .isEqualTo("google-genai");
        // The Ollama/OpenAI selector is still resolved, and resolved to "no provider" rather than
        // left unset — otherwise their matchIfMissing autoconfigurations would claim the bean.
        assertThat(environment.getProperty(EmbeddingProviderEnvironmentPostProcessor.EMBEDDING_SELECTOR_PROPERTY))
                .isEqualTo(EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER);
    }

    /**
     * And the mirror image, which is the failure this per-selector resolution exists to prevent:
     * pinning only the Gemini selector must still switch the Ollama/OpenAI one off. Leaving it unset
     * does not disable them — {@code matchIfMissing = true} means an unset selector activates them,
     * so they would claim the {@code EmbeddingModel} and the context would fail to start on a
     * missing credential.
     */
    @Test
    void pinningOnlyTheGeminiSelectorStillSwitchesTheOtherOneOff() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GEMINI_API_KEY", "not-a-real-key")
                .withProperty(EmbeddingProviderEnvironmentPostProcessor.TEXT_EMBEDDING_SELECTOR_PROPERTY, "google-genai");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(EmbeddingProviderEnvironmentPostProcessor.EMBEDDING_SELECTOR_PROPERTY))
                .isEqualTo(EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER);
    }

    /**
     * Both selectors come from one property source, so a single decision moves both and cannot be
     * half-applied.
     */
    @Test
    void derivesBothSelectorsFromASingleDecision() {
        MockEnvironment environment = new MockEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        Map<String, Object> decisions = (Map<String, Object>) environment.getPropertySources()
                .get(EmbeddingProviderEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)
                .getSource();

        assertThat(decisions).containsKeys(
                EmbeddingProviderEnvironmentPostProcessor.EMBEDDING_SELECTOR_PROPERTY,
                EmbeddingProviderEnvironmentPostProcessor.TEXT_EMBEDDING_SELECTOR_PROPERTY);
    }

    // --- ordering ------------------------------------------------------------------------

    /**
     * {@code LOWEST_PRECEDENCE} so this runs after {@code ConfigDataEnvironmentPostProcessor},
     * which is what makes {@code application.yaml} and {@code .env} visible on the environment by
     * the time the decision is made — and what lets an override there be seen.
     */
    @Test
    void runsAfterConfigDataHasBeenLoaded() {
        assertThat(processor.getOrder()).isEqualTo(org.springframework.core.Ordered.LOWEST_PRECEDENCE);
    }
}