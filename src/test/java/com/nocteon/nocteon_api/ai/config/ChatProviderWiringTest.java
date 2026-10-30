package com.nocteon.nocteon_api.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

/**
 * Which {@code ChatModel} actually lands in the context, and which {@code EmbeddingModel}.
 *
 * <p>{@code ChatProviderEnvironmentPostProcessorTest} and
 * {@code EmbeddingProviderEnvironmentPostProcessorTest} pin the decisions;
 * {@code ChatProviderEnvironmentPostProcessor} and
 * {@code EmbeddingProviderEnvironmentPostProcessor} are wired here for real, through the same
 * autoconfigurations the application uses. No network is involved: none of these providers contacts
 * its backend while a model bean is being constructed, so a placeholder key is enough to prove which
 * bean is the one selected.
 *
 * <p>This is the test that would have caught the failure mode this whole change exists to avoid.
 * Every provider declares its selector {@code matchIfMissing = true}, so an unset selector does not
 * mean "default" — it means every provider on the classpath tries to define the bean and
 * {@code PgVectorStore}'s by-type injection fails at startup. The {@code singleBean} assertions
 * below are what makes that visible.
 */
class ChatProviderWiringTest {

    /**
     * A placeholder key: never sent anywhere, only used to make the provider selection non-blank.
     *
     * <p>Set twice because it is read twice, by two different mechanisms. {@code GROQ_API_KEY} is
     * what {@link ChatProviderEnvironmentPostProcessor} reads to choose the provider;
     * {@code spring.ai.openai.api-key} is what the OpenAI client is actually constructed from.
     * In the application these are one env var because {@code application.yaml} wires them
     * together with a placeholder; {@link ApplicationContextRunner} does not load that file, so the
     * test supplies both.
     */
    private static final String GROQ_KEY = "GROQ_API_KEY=gsk_not-a-real-key";

    private static final String GROQ_API_KEY_PROPERTY = "spring.ai.openai.api-key=gsk_not-a-real-key";

    private static final String GROQ_BASE_URL = "spring.ai.openai.base-url=https://api.groq.com/openai";

    private static final String GROQ_MODEL = "spring.ai.openai.chat.options.model=openai/gpt-oss-20b";

    /**
     * The Gemini key, supplied the same two ways as the Groq one: {@code GEMINI_API_KEY} is what
     * {@link EmbeddingProviderEnvironmentPostProcessor} reads to choose the provider, and
     * {@code spring.ai.google.genai.embedding.api-key} is what the SDK client is constructed from.
     *
     * <p>Not a real key, and no request is made — only the selection is under test.
     */
    private static final String GEMINI_KEY = "GEMINI_API_KEY=not-a-real-key";

    private static final String GEMINI_API_KEY_PROPERTY = "spring.ai.google.genai.embedding.api-key=not-a-real-key";

    private static final String GEMINI_MODEL = "spring.ai.google.genai.embedding.text.model=gemini-embedding-2";

    private static final String GEMINI_DIMENSIONS = "spring.ai.google.genai.embedding.text.dimensions=768";

    /**
     * Every provider autoconfiguration the application's classpath offers.
     *
     * <p>A list rather than an {@code AutoConfigurations} constant because
     * {@code AutoConfigurations} exposes no filter in Spring Boot 4, and the exclusion below has to
     * be applied to the list before it is handed over.
     */
    private static final List<Class<?>> EVERY_PROVIDER = List.of(
            org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration.class,
            org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration.class,
            org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration.class,
            org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration.class,
            org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration.class,
            org.springframework.ai.model.google.genai.autoconfigure.embedding
                    .GoogleGenAiEmbeddingConnectionAutoConfiguration.class,
            org.springframework.ai.model.google.genai.autoconfigure.embedding
                    .GoogleGenAiTextEmbeddingAutoConfiguration.class,
            // Both chat beans take a ToolCallingManager, which its own autoconfiguration supplies.
            // Without it the Groq cases fail to start for a reason unrelated to provider selection,
            // which would make this test untestable rather than wrong.
            org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration.class);

    /**
     * A runner whose environment the two post-processors have actually decided for.
     *
     * <p>Two details of {@link ApplicationContextRunner} have to be mirrored here, and both are
     * limitations of the test harness rather than of the code:
     * <ul>
     * <li>It takes an explicit autoconfiguration list, and an explicit list does not go through
     * Spring Boot's import selector — so {@code spring.autoconfigure.exclude} has no effect on it.
     * The decision is therefore computed by running the post-processor over the same properties
     * and the result applied as a filter, which is what Boot does with it.</li>
     * <li>An {@code EnvironmentPostProcessor} is not an {@code ApplicationContextInitializer}, so
     * it cannot be handed to {@code withInitializer} directly. Applying it to the context's
     * environment is the same thing the real lifecycle does, at a point before refresh — and
     * therefore before any {@code @ConditionalOnProperty} is evaluated.</li>
     * </ul>
     */
    private static ApplicationContextRunner runnerFor(String... properties) {
        MockEnvironment probe = new MockEnvironment();
        for (String property : properties) {
            int separator = property.indexOf('=');
            probe.setProperty(property.substring(0, separator), property.substring(separator + 1));
        }

        new EmbeddingProviderEnvironmentPostProcessor()
                .postProcessEnvironment(probe, new SpringApplication());

        String excluded = probe.getProperty("spring.autoconfigure.exclude", "");
        Set<String> excludedClasses = Arrays.stream(excluded.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        Class<?>[] remaining = EVERY_PROVIDER.stream()
                .filter(type -> !excludedClasses.contains(type.getName()))
                .toArray(Class<?>[]::new);

        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(remaining))
                .withPropertyValues(properties)
                .withInitializer(context -> {
                    new ChatProviderEnvironmentPostProcessor()
                            .postProcessEnvironment(context.getEnvironment(), new SpringApplication());
                    new EmbeddingProviderEnvironmentPostProcessor()
                            .postProcessEnvironment(context.getEnvironment(), new SpringApplication());
                });
    }

    private final ApplicationContextRunner runner = runnerFor();

    /**
     * No key: the local model, and exactly one {@code ChatModel} in the context.
     *
     * <p>This is the zero-API-key path the portfolio demo depends on, and the
     * {@code doesNotHaveBean(OpenAiChatModel.class)} assertion is what proves no half-configured
     * Groq client is lying around waiting to fail on the first visitor message.
     */
    @Test
    void withoutAnApiKeyTheLocalModelIsTheOnlyChatModel() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ChatModel.class);
            assertThat(context).hasSingleBean(OllamaChatModel.class);
            assertThat(context).doesNotHaveBean(OpenAiChatModel.class);
        });
    }

    /**
     * A key: Groq, and still exactly one {@code ChatModel}.
     *
     * <p>The base URL is set explicitly rather than relying on the application's
     * {@code application.yaml}, because this runner does not load it. The value is not a valid
     * Groq endpoint and nothing calls it — only the selection is under test.
     */
    @Test
    void withAnApiKeyTheGroqModelIsTheOnlyChatModel() {
        runnerFor(GROQ_KEY, GROQ_API_KEY_PROPERTY, GROQ_BASE_URL, GROQ_MODEL).run(context -> {
            assertThat(context).hasSingleBean(ChatModel.class);
            assertThat(context).hasSingleBean(OpenAiChatModel.class);
            assertThat(context).doesNotHaveBean(OllamaChatModel.class);
        });
    }

    /**
     * A Gemini key moves the <em>embedding</em> model without touching the chat model: chat stays on
     * Groq because {@code GROQ_API_KEY} is what selects it. The two decisions are independent, and
     * conflating them — which a single shared selector would do — is exactly what would make it
     * impossible to keep Groq chat while moving embeddings to Gemini.
     */
    @Test
    void aGeminiKeyMovesTheEmbeddingModelAndLeavesChatOnGroq() {
        runnerFor(GROQ_KEY, GROQ_API_KEY_PROPERTY, GROQ_BASE_URL, GROQ_MODEL,
                GEMINI_KEY, GEMINI_API_KEY_PROPERTY, GEMINI_MODEL, GEMINI_DIMENSIONS,
                "AI_EMBEDDING_PROVIDER=gemini").run(context -> {
                    assertThat(context).hasSingleBean(ChatModel.class);
                    assertThat(context).hasSingleBean(OpenAiChatModel.class);

                    assertThat(context).hasSingleBean(EmbeddingModel.class);
                    assertThat(context.getBean(EmbeddingModel.class))
                            .isInstanceOf(GoogleGenAiTextEmbeddingModel.class);
                    assertThat(context).doesNotHaveBean(OllamaEmbeddingModel.class);
                });
    }

    /**
     * The deployed shape: embeddings on Gemini, so no local model server is needed for them at all.
     * The chat fallback still exists, but a deployed app with both keys configured must not be
     * silently running its index through a local model that will not be there.
     */
    @Test
    void aGeminiKeyIsWhatSelectsTheHostedEmbeddingModel() {
        runnerFor(GEMINI_KEY, GEMINI_API_KEY_PROPERTY, GEMINI_MODEL, GEMINI_DIMENSIONS,
                "AI_EMBEDDING_PROVIDER=gemini").run(context -> {
            assertThat(context).hasSingleBean(EmbeddingModel.class);
            assertThat(context.getBean(EmbeddingModel.class))
                    .isInstanceOf(GoogleGenAiTextEmbeddingModel.class);
        });
    }

    /**
     * 768 output dimensions are requested explicitly rather than relying on the model default.
     *
     * <p>This is why the model swap needs no schema change: the {@code vector_store} column is
     * already {@code vector(768)}, and {@code PgVectorStore} is constructed with the configured
     * dimension. Asserted on the configured options rather than on a live call, because a live call
     * is exactly what this class otherwise avoids — the live behaviour is verified against the real
     * API separately.
     */
    @Test
    void theGeminiEmbeddingModelIsConfiguredForTheExistingVectorColumnWidth() {
        runnerFor(GEMINI_KEY, GEMINI_API_KEY_PROPERTY, GEMINI_MODEL, GEMINI_DIMENSIONS,
                "AI_EMBEDDING_PROVIDER=gemini").run(context -> {
            GoogleGenAiTextEmbeddingModel model = context.getBean(GoogleGenAiTextEmbeddingModel.class);
            assertThat(model.options.getModel()).isEqualTo("gemini-embedding-2");
            assertThat(model.options.getDimensions()).isEqualTo(768);
        });
    }

    /**
     * The zero-API-key path still works, and it still embeds locally — this is the case the
     * portfolio demo runs on a machine with no keys at all, so a regression here is a broken demo.
     */
    @Test
    void theEmbeddingModelStaysLocalWithoutAnApiKey() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(EmbeddingModel.class);
            assertThat(context.getBean(EmbeddingModel.class)).isInstanceOf(OllamaEmbeddingModel.class);
            assertThat(context).doesNotHaveBean(GoogleGenAiTextEmbeddingModel.class);
        });
    }

    /**
     * A Groq key alone must <em>not</em> drag the embedding model to a hosted provider. Groq has no
     * embedding model in this project, and its presence says nothing about Gemini: coupling the two
     * would make the hosted embedding path unreachable without a chat key, and would send the index
     * to a provider the project never configured.
     */
    @Test
    void aGroqKeyAloneLeavesTheEmbeddingModelLocal() {
        runnerFor(GROQ_KEY, GROQ_API_KEY_PROPERTY, GROQ_BASE_URL, GROQ_MODEL).run(context -> {
            assertThat(context).hasSingleBean(EmbeddingModel.class);
            assertThat(context.getBean(EmbeddingModel.class)).isInstanceOf(OllamaEmbeddingModel.class);
        });
    }

    /**
     * A blank {@code GEMINI_API_KEY} — an unset variable in a {@code .env} file — falls back to the
     * local model rather than selecting a provider the app cannot authenticate against.
     */
    @Test
    void aBlankGeminiKeyFallsBackToTheLocalModel() {
        runnerFor("GEMINI_API_KEY=").run(context -> {
            assertThat(context).hasSingleBean(EmbeddingModel.class);
            assertThat(context.getBean(EmbeddingModel.class)).isInstanceOf(OllamaEmbeddingModel.class);
        });
    }

    /**
     * An explicit selector wins, and no Gemini model bean is built for a run that did not ask for
     * one — the escape hatch that lets a machine with a key in its environment still use the local
     * model.
     */
    @Test
    void anExplicitEmbeddingSelectorIsHonoured() {
        runnerFor(GEMINI_KEY, GEMINI_API_KEY_PROPERTY, GEMINI_MODEL, GEMINI_DIMENSIONS,
                "spring.ai.model.embedding.text=none")
                .run(context -> assertThat(context).doesNotHaveBean(GoogleGenAiTextEmbeddingModel.class));
    }

    /**
     * Pinning the local model explicitly on a machine that <em>does</em> have a Gemini key must not
     * leave the un-gated Gemini connection autoconfiguration behind, which would then fail on a
     * missing project id. The exclusion follows the pinned provider, not the environment's keys.
     */
    @Test
    void pinningTheLocalModelAlsoRemovesTheGeminiConnection() {
        runnerFor(GEMINI_KEY, "spring.ai.model.embedding=ollama",
                "spring.ai.model.embedding.text=none").run(context -> {
                    assertThat(context).hasSingleBean(EmbeddingModel.class);
                    assertThat(context.getBean(EmbeddingModel.class)).isInstanceOf(OllamaEmbeddingModel.class);
                    assertThat(context).doesNotHaveBean(
                            org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails.class);
                });
    }

    /**
     * Why the selector has to be derived rather than left unset.
     *
     * <p>Without it — with the Ollama, OpenAI and Gemini embedding autoconfigurations all present —
     * this context does not start at all. Every one of them is {@code matchIfMissing = true}, so they
     * all claim the {@code EmbeddingModel}; {@code OpenAiEmbeddingAutoConfiguration} then fails at
     * construction with "At least one credential source must be specified".
     * {@code PgVectorStore} is constructed with that bean, so the failure would surface as the
     * application refusing to boot on a machine with no API key — the exact configuration the
     * portfolio demo runs in.
     */
    @Test
    void withoutTheEmbeddingSelectorTheContextFailsToStartWithoutAnApiKey() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration.class,
                        org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration.class,
                        org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration.class,
                        org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration.class))
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("credential"));
    }

    /**
     * The Gemini starter alone breaks the no-key path unless its connection autoconfiguration is
     * excluded. This is the specific failure
     * {@code EmbeddingProviderEnvironmentPostProcessor} exists to prevent, pinned in isolation:
     * {@code GoogleGenAiEmbeddingConnectionAutoConfiguration} carries only {@code @ConditionalOnClass}
     * and {@code @ConditionalOnMissingBean}, so selecting a different embedding provider cannot
     * switch it off and a blank key turns into
     * {@code Google GenAI project-id must be set!} at startup.
     */
    @Test
    void theGeminiConnectionAutoconfigurationFailsWithoutAKeyOrProjectId() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration.class,
                        org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration.class,
                        org.springframework.ai.model.google.genai.autoconfigure.embedding
                                .GoogleGenAiEmbeddingConnectionAutoConfiguration.class,
                        org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration.class))
                .withPropertyValues(EmbeddingProviderEnvironmentPostProcessor.TEXT_EMBEDDING_SELECTOR_PROPERTY
                        + "=none")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("project-id must be set"));
    }

    /** Which autoconfigurations the local path drops, asserted so the exclusion cannot be deleted silently. */
    @Test
    void theLocalPathExcludesOnlyTheGeminiConnection() {
        MockEnvironment probe = new MockEnvironment();
        new EmbeddingProviderEnvironmentPostProcessor()
                .postProcessEnvironment(probe, new SpringApplication());

        assertThat(List.of(probe.getProperty("spring.autoconfigure.exclude", "").split(",")))
                .containsExactly(
                        EmbeddingProviderEnvironmentPostProcessor.GOOGLE_GENAI_CONNECTION_AUTOCONFIGURATION);
    }

    /** On the Gemini path nothing is excluded, or the provider the key selected would be switched off. */
    @Test
    void theGeminiPathExcludesNothing() {
        MockEnvironment probe = new MockEnvironment()
                .withProperty("GEMINI_API_KEY", "not-a-real-key")
                .withProperty("AI_EMBEDDING_PROVIDER", "gemini");
        new EmbeddingProviderEnvironmentPostProcessor()
                .postProcessEnvironment(probe, new SpringApplication());

        assertThat(probe.getProperty("spring.autoconfigure.exclude")).isNull();
    }
}