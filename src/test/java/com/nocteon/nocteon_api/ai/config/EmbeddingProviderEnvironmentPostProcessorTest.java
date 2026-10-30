package com.nocteon.nocteon_api.ai.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

/**
 * The embedding provider decision, in one table.
 *
 * <p>The rule is: {@code AI_EMBEDDING_PROVIDER} first, then {@code GEMINI_API_KEY}, then
 * {@code EMBEDDING_API_KEY}, then nothing — and nothing means keyword-only, never Ollama. Ollama
 * is reachable only through the explicit switch, which is the whole point of the change this test
 * pins.
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

    private MockEnvironment decide(MockEnvironment environment) {
        processor.postProcessEnvironment(environment, new SpringApplication());
        return environment;
    }

    private static String provider(MockEnvironment environment) {
        return environment.getProperty(EmbeddingProviderEnvironmentPostProcessor.PROVIDER_DECISION_PROPERTY);
    }

    private static String embeddingSelector(MockEnvironment environment) {
        return environment.getProperty(EmbeddingProviderEnvironmentPostProcessor.EMBEDDING_SELECTOR_PROPERTY);
    }

    private static String textSelector(MockEnvironment environment) {
        return environment.getProperty(EmbeddingProviderEnvironmentPostProcessor.TEXT_EMBEDDING_SELECTOR_PROPERTY);
    }

    // --- no key at all: keyword-only ----------------------------------------------------

    /**
     * The rule this whole change exists for. A machine with no embedding key gets no embedding
     * model, no vector store and no call to a local model server — search stays keyword-only.
     */
    @Test
    void withNoKeyAtAllTheDecisionIsKeywordOnly() {
        MockEnvironment environment = decide(new MockEnvironment());

        assertThat(provider(environment)).isEqualTo(EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER);
        assertThat(embeddingSelector(environment)).isEqualTo(EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER);
        assertThat(textSelector(environment)).isEqualTo(EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER);
    }

    /**
     * And the vector index is switched off with it, because {@code PgVectorStore} cannot be built
     * without an {@code EmbeddingModel}. Leaving this selector alone — it is
     * {@code matchIfMissing = true} — would turn "no key" into a boot failure.
     */
    @Test
    void withNoKeyAtAllTheVectorStoreIsSwitchedOff() {
        MockEnvironment environment = decide(new MockEnvironment());

        assertThat(environment.getProperty(EmbeddingProviderEnvironmentPostProcessor.VECTOR_STORE_TYPE_PROPERTY))
                .isEqualTo(EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER);
    }

    /**
     * A Gemini key that is present but out of quota is not a reason to quietly embed somewhere
     * else: the failure is loud at index time and the configuration says what was asked for.
     */
    @Test
    void aGeminiKeySelectsGeminiEvenThoughItsQuotaCannotBeKnownHere() {
        MockEnvironment environment = decide(new MockEnvironment().withProperty("GEMINI_API_KEY", "not-a-real-key"));

        assertThat(provider(environment)).isEqualTo(EmbeddingProviderEnvironmentPostProcessor.GOOGLE_GENAI_PROVIDER);
        assertThat(textSelector(environment)).isEqualTo(EmbeddingProviderEnvironmentPostProcessor.GOOGLE_GENAI_PROVIDER);
        assertThat(embeddingSelector(environment)).isEqualTo(EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER);
        assertThat(environment.getProperty(EmbeddingProviderEnvironmentPostProcessor.VECTOR_STORE_TYPE_PROPERTY))
                .isNull();
    }

    /** A key of nothing but whitespace — an unset variable in a .env file — is not a key. */
    @Test
    void treatsBlankAndWhitespaceOnlyKeysAsAbsent() {
        assertThat(provider(decide(new MockEnvironment().withProperty("GEMINI_API_KEY", ""))))
                .isEqualTo(EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER);
        assertThat(provider(decide(new MockEnvironment().withProperty("GEMINI_API_KEY", "   "))))
                .isEqualTo(EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER);
        assertThat(provider(decide(new MockEnvironment().withProperty("EMBEDDING_API_KEY", ""))))
                .isEqualTo(EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER);
    }

    /**
     * Groq is chat only. Its key must not drag embeddings anywhere, and it certainly must not be
     * mistaken for an embeddings credential — the API has no embeddings endpoint.
     */
    @Test
    void aGroqKeyAloneLeavesEmbeddingsKeywordOnly() {
        MockEnvironment environment = decide(new MockEnvironment().withProperty("GROQ_API_KEY", "gsk_not-a-real-key"));

        assertThat(provider(environment)).isEqualTo(EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER);
        assertThat(environment.getProperty(EmbeddingProviderEnvironmentPostProcessor.VECTOR_STORE_TYPE_PROPERTY))
                .isEqualTo(EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER);
    }

    // --- the generic hosted provider ----------------------------------------------------

    /**
     * A generic hosted key selects the OpenAI-compatible provider without any other switch: the
     * point of that path is that an operator can point the project at a vendor this code has never
     * heard of, with credentials only.
     */
    @Test
    void anEmbeddingKeyAloneSelectsTheGenericHostedProvider() {
        MockEnvironment environment = decide(new MockEnvironment()
                .withProperty("EMBEDDING_API_KEY", "not-a-real-key")
                .withProperty("EMBEDDING_BASE_URL", "https://api.voyageai.com/v1")
                .withProperty("EMBEDDING_MODEL", "voyage-3-lite"));

        assertThat(provider(environment)).isEqualTo(EmbeddingProviderEnvironmentPostProcessor.OPENAI_PROVIDER);
        assertThat(embeddingSelector(environment))
                .isEqualTo(EmbeddingProviderEnvironmentPostProcessor.OPENAI_PROVIDER);
        assertThat(textSelector(environment)).isEqualTo(EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER);
    }

    /** Gemini is preferred when both keys are present; the switch is how the other one is forced. */
    @Test
    void geminiWinsWhenBothHostedKeysArePresent() {
        MockEnvironment environment = decide(new MockEnvironment()
                .withProperty("GEMINI_API_KEY", "not-a-real-key")
                .withProperty("EMBEDDING_API_KEY", "not-a-real-key"));

        assertThat(provider(environment)).isEqualTo(EmbeddingProviderEnvironmentPostProcessor.GOOGLE_GENAI_PROVIDER);
    }

    @Test
    void theSwitchForcesTheGenericProviderOverGemini() {
        MockEnvironment environment = decide(new MockEnvironment()
                .withProperty("GEMINI_API_KEY", "not-a-real-key")
                .withProperty("EMBEDDING_API_KEY", "not-a-real-key")
                .withProperty(EmbeddingProviderEnvironmentPostProcessor.AI_EMBEDDING_PROVIDER_PROPERTY,
                        "openai-compatible"));

        assertThat(provider(environment)).isEqualTo(EmbeddingProviderEnvironmentPostProcessor.OPENAI_PROVIDER);
        assertThat(textSelector(environment)).isEqualTo(EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER);
    }

    /**
     * Asking for the generic provider with no key is a configuration error, not a state to degrade
     * from: there is nothing to authenticate against, so the only honest answer is to refuse.
     */
    @Test
    void askingForTheGenericProviderWithoutAKeyFailsStartup() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(EmbeddingProviderEnvironmentPostProcessor.AI_EMBEDDING_PROVIDER_PROPERTY,
                        "openai-compatible");

        assertThatThrownBy(() -> decide(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EMBEDDING_API_KEY");
    }

    /**
     * And the same for Gemini, with a message about the AI Studio key rather than the Vertex AI
     * project id the starter would complain about.
     */
    @Test
    void askingForGeminiWithoutAKeyFailsStartup() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(EmbeddingProviderEnvironmentPostProcessor.AI_EMBEDDING_PROVIDER_PROPERTY,
                        "gemini");

        assertThatThrownBy(() -> decide(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GEMINI_API_KEY")
                .hasMessageContaining("AI Studio");
    }

    // --- Ollama, opt-in only ------------------------------------------------------------

    /**
     * The only route to a local model. Everything else in this class is about making sure nobody
     * reaches it by accident.
     */
    @Test
    void ollamaIsSelectedOnlyWhenExplicitlyAskedFor() {        MockEnvironment environment = decide(new MockEnvironment()
                .withProperty(EmbeddingProviderEnvironmentPostProcessor.AI_EMBEDDING_PROVIDER_PROPERTY, "ollama"));

        assertThat(provider(environment)).isEqualTo(EmbeddingProviderEnvironmentPostProcessor.OLLAMA_PROVIDER);
        assertThat(embeddingSelector(environment)).isEqualTo(EmbeddingProviderEnvironmentPostProcessor.OLLAMA_PROVIDER);
        assertThat(textSelector(environment)).isEqualTo(EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER);
        assertThat(environment.getProperty(EmbeddingProviderEnvironmentPostProcessor.VECTOR_STORE_TYPE_PROPERTY))
                .isNull();
    }

    /** And it stays selected when a hosted key is also present, because the switch was explicit. */
    @Test
    void anExplicitOllamaChoiceBeatsAHostedKey() {
        MockEnvironment environment = decide(new MockEnvironment()
                .withProperty("GEMINI_API_KEY", "not-a-real-key")
                .withProperty(EmbeddingProviderEnvironmentPostProcessor.AI_EMBEDDING_PROVIDER_PROPERTY, "ollama"));

        assertThat(embeddingSelector(environment)).isEqualTo(EmbeddingProviderEnvironmentPostProcessor.OLLAMA_PROVIDER);
        assertThat(textSelector(environment)).isEqualTo(EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER);
    }

    /** The switch is an operator-facing string: case and the documented aliases are accepted. */
    @Test
    void acceptsTheDocumentedAliases() {
        assertThat(EmbeddingProviderEnvironmentPostProcessor.normalise("Gemini"))
                .isEqualTo(EmbeddingProviderEnvironmentPostProcessor.GOOGLE_GENAI_PROVIDER);
        assertThat(EmbeddingProviderEnvironmentPostProcessor.normalise("google"))
                .isEqualTo(EmbeddingProviderEnvironmentPostProcessor.GOOGLE_GENAI_PROVIDER);
        assertThat(EmbeddingProviderEnvironmentPostProcessor.normalise("OpenAI-Compatible"))
                .isEqualTo(EmbeddingProviderEnvironmentPostProcessor.OPENAI_PROVIDER);
        assertThat(EmbeddingProviderEnvironmentPostProcessor.normalise("local"))
                .isEqualTo(EmbeddingProviderEnvironmentPostProcessor.OLLAMA_PROVIDER);
        assertThat(EmbeddingProviderEnvironmentPostProcessor.normalise("keyword-only"))
                .isEqualTo(EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER);
    }

    /**
     * A typo must not land on a provider nobody asked for. Embedded in a word like "none" the
     * failure would be invisible until somebody compared the vectors.
     */
    @Test
    void rejectsAnUnknownProviderValue() {
        assertThatThrownBy(() -> EmbeddingProviderEnvironmentPostProcessor.normalise("gemnii"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gemnii");
    }

    @Test
    void anExplicitNoneKeepsSearchKeywordOnlyAndNeedsNoKey() {
        MockEnvironment environment = decide(new MockEnvironment()
                .withProperty("GEMINI_API_KEY", "not-a-real-key")
                .withProperty(EmbeddingProviderEnvironmentPostProcessor.AI_EMBEDDING_PROVIDER_PROPERTY, "none"));

        assertThat(provider(environment)).isEqualTo(EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER);
        assertThat(environment.getProperty(EmbeddingProviderEnvironmentPostProcessor.VECTOR_STORE_TYPE_PROPERTY))
                .isEqualTo(EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER);
    }

    // --- rejected providers -------------------------------------------------------------

    /**
     * Every selector is {@code matchIfMissing = true}, so a rejected provider has to be switched
     * off explicitly. Leaving the Ollama/OpenAI selector unset while switching on Gemini is not a
     * tie-break — it is several autoconfigurations defining an {@code EmbeddingModel}, which is the
     * boot failure this class exists to prevent.
     */
    @Test
    void switchesOffEveryRejectedProvider() {
        assertThat(embeddingSelector(decide(new MockEnvironment()
                .withProperty("GEMINI_API_KEY", "not-a-real-key"))))
                .isEqualTo(EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER);

        assertThat(textSelector(decide(new MockEnvironment()
                .withProperty("EMBEDDING_API_KEY", "not-a-real-key"))))
                .isEqualTo(EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER);
    }

    // --- explicit overrides -------------------------------------------------------------

    /**
     * An operator pinning the provider wins over the derivation, on either selector. This is what
     * lets a machine that has a key in its environment run one directory against another store.
     */
    @Test
    void anExplicitOllamaSelectorOverridesTheDerivedValue() {
        MockEnvironment environment = decide(new MockEnvironment()
                .withProperty("GEMINI_API_KEY", "not-a-real-key")
                .withProperty(EmbeddingProviderEnvironmentPostProcessor.EMBEDDING_SELECTOR_PROPERTY, "ollama"));

        assertThat(embeddingSelector(environment)).isEqualTo(EmbeddingProviderEnvironmentPostProcessor.OLLAMA_PROVIDER);
        assertThat(provider(environment)).isEqualTo(EmbeddingProviderEnvironmentPostProcessor.OLLAMA_PROVIDER);
    }

    @Test
    void anExplicitTextSelectorOverridesTheDerivedValue() {
        MockEnvironment environment = decide(new MockEnvironment()
                .withProperty("GEMINI_API_KEY", "not-a-real-key")
                .withProperty(EmbeddingProviderEnvironmentPostProcessor.TEXT_EMBEDDING_SELECTOR_PROPERTY, "none"));

        assertThat(textSelector(environment)).isEqualTo("none");
        // The Ollama/OpenAI selector is still resolved, and resolved to "no provider" rather than
        // left unset — otherwise their matchIfMissing autoconfigurations would claim the bean.
        assertThat(embeddingSelector(environment)).isEqualTo(EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER);
    }

    /**
     * A pinned Gemini selector wins over the key, and must not be undone by the absence of one.
     */
    @Test
    void pinningGeminiSelectsGeminiEvenWithoutAKey() {
        MockEnvironment environment = decide(new MockEnvironment()
                .withProperty(EmbeddingProviderEnvironmentPostProcessor.TEXT_EMBEDDING_SELECTOR_PROPERTY, "google-genai"));

        assertThat(textSelector(environment)).isEqualTo(EmbeddingProviderEnvironmentPostProcessor.GOOGLE_GENAI_PROVIDER);
        assertThat(embeddingSelector(environment)).isEqualTo(EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER);
        assertThat(environment.getProperty(EmbeddingProviderEnvironmentPostProcessor.VECTOR_STORE_TYPE_PROPERTY))
                .isNull();
    }

    /** Pinning both selectors off is the same state as no key at all, and disables the store. */
    @Test
    void pinningBothSelectorsOffAlsoDisablesTheVectorStore() {
        MockEnvironment environment = decide(new MockEnvironment()
                .withProperty(EmbeddingProviderEnvironmentPostProcessor.EMBEDDING_SELECTOR_PROPERTY, "none")
                .withProperty(EmbeddingProviderEnvironmentPostProcessor.TEXT_EMBEDDING_SELECTOR_PROPERTY, "none"));

        assertThat(provider(environment)).isEqualTo(EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER);
        assertThat(environment.getProperty(EmbeddingProviderEnvironmentPostProcessor.VECTOR_STORE_TYPE_PROPERTY))
                .isEqualTo(EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER);
    }

    // --- the Gemini connection autoconfiguration ----------------------------------------

    /**
     * The pinned value survives untouched, and the un-gated Gemini connection autoconfiguration is
     * removed with it — otherwise a pinned local provider on a machine with a key would fail on a
     * missing project id.
     */
    @Test
    void pinningALocalProviderAlsoRemovesTheGeminiConnection() {
        MockEnvironment environment = decide(new MockEnvironment()
                .withProperty("GEMINI_API_KEY", "not-a-real-key")
                .withProperty(EmbeddingProviderEnvironmentPostProcessor.EMBEDDING_SELECTOR_PROPERTY, "ollama")
                .withProperty(EmbeddingProviderEnvironmentPostProcessor.TEXT_EMBEDDING_SELECTOR_PROPERTY, "none"));

        assertThat(environment.getProperty("spring.autoconfigure.exclude"))
                .isEqualTo(EmbeddingProviderEnvironmentPostProcessor.GOOGLE_GENAI_CONNECTION_AUTOCONFIGURATION);
    }

    /** Pinning Gemini must leave the connection in place, or the selected provider has nothing to build on. */
    @Test
    void pinningGeminiKeepsTheConnection() {
        MockEnvironment environment = decide(new MockEnvironment()
                .withProperty(EmbeddingProviderEnvironmentPostProcessor.TEXT_EMBEDDING_SELECTOR_PROPERTY, "google-genai"));

        assertThat(environment.getProperty("spring.autoconfigure.exclude")).isNull();
    }

    /** The generic provider is not Gemini either, so the connection still has to go. */
    @Test
    void theGenericProviderExcludesTheGeminiConnection() {
        MockEnvironment environment = decide(new MockEnvironment()
                .withProperty("EMBEDDING_API_KEY", "not-a-real-key"));

        assertThat(environment.getProperty("spring.autoconfigure.exclude"))
                .isEqualTo(EmbeddingProviderEnvironmentPostProcessor.GOOGLE_GENAI_CONNECTION_AUTOCONFIGURATION);
    }

    // --- structure ----------------------------------------------------------------------

    /**
     * Both selectors and the decision come from one property source, so a single decision moves
     * them together and cannot be half-applied.
     */
    @Test
    void derivesEverythingFromASingleDecision() {
        MockEnvironment environment = decide(new MockEnvironment());

        Map<String, Object> decisions = (Map<String, Object>) environment.getPropertySources()
                .get(EmbeddingProviderEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)
                .getSource();

        assertThat(decisions).containsKeys(
                EmbeddingProviderEnvironmentPostProcessor.EMBEDDING_SELECTOR_PROPERTY,
                EmbeddingProviderEnvironmentPostProcessor.TEXT_EMBEDDING_SELECTOR_PROPERTY,
                EmbeddingProviderEnvironmentPostProcessor.VECTOR_STORE_TYPE_PROPERTY,
                EmbeddingProviderEnvironmentPostProcessor.PROVIDER_DECISION_PROPERTY);
    }

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
