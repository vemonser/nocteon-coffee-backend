package com.nocteon.nocteon_api.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Which {@code ChatModel} actually lands in the context, and which {@code EmbeddingModel}.
 *
 * <p>{@code ChatProviderEnvironmentPostProcessorTest} pins the decision;
 * {@code ChatProviderEnvironmentPostProcessor} is wired here for real, through the same
 * autoconfigurations the application uses. No network is involved: neither provider contacts its
 * backend while a {@code ChatModel} is being constructed, so a placeholder key is enough to prove
 * the Groq bean is the one selected.
 *
 * <p>This is the test that would have caught the failure mode this whole change exists to avoid.
 * Both starters declare {@code spring.ai.model.chat} with {@code matchIfMissing = true}, so an
 * unset selector does not mean "default" — it means both providers try to define a
 * {@code ChatModel} and {@code AiChatService}'s by-type injection fails at startup. The
 * {@code singleBean} assertions below are what makes that visible.
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

    private static final String EMBEDDING_PINNED_LOCAL = "spring.ai.model.embedding=ollama";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration.class,
                    org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration.class,
                    org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration.class,
                    org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration.class,
                    org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration.class,
                    // Both chat beans take a ToolCallingManager, which its own autoconfiguration
                    // supplies. Without it the Groq cases fail to start for a reason unrelated to
                    // provider selection, which would make this test untestable rather than wrong.
                    org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration.class))
            // An EnvironmentPostProcessor is not an ApplicationContextInitializer, so it cannot be
            // handed to withInitializer directly. Applying it to the context's environment is the
            // same thing the real lifecycle does, at a point before refresh — and therefore before
            // any @ConditionalOnProperty is evaluated.
            .withInitializer(context -> new ChatProviderEnvironmentPostProcessor()
                    .postProcessEnvironment(context.getEnvironment(), new SpringApplication()));

    /**
     * No key: the local model, and exactly one {@code ChatModel} in the context.
     *
     * <p>This is the zero-API-key path the portfolio demo depends on, and the
     * {@code doesNotHaveBean(OpenAiChatModel.class)} assertion is what proves no half-configured
     * Groq client is lying around waiting to fail on the first visitor message.
     */
    @Test
    void withoutAnApiKeyTheLocalModelIsTheOnlyChatModel() {
        runner.withPropertyValues(EMBEDDING_PINNED_LOCAL)
                .run(context -> {
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
        runner.withPropertyValues(GROQ_KEY, GROQ_API_KEY_PROPERTY, GROQ_BASE_URL, GROQ_MODEL,
                        EMBEDDING_PINNED_LOCAL)
                .run(context -> {
                    assertThat(context).hasSingleBean(ChatModel.class);
                    assertThat(context).hasSingleBean(OpenAiChatModel.class);
                    assertThat(context).doesNotHaveBean(OllamaChatModel.class);
                });
    }

    /**
     * The embedding model stays local in both configurations.
     *
     * <p>The requirement, and the reason for it: semantic search must not gain an external
     * dependency or a quota. It also runs on the index path, where a hosted call would be paid
     * on every document and every reindex. The application pins
     * {@code spring.ai.model.embedding=ollama}; with both starters present an unpinned value
     * would let {@code OpenAiEmbeddingAutoConfiguration} win, and {@code PgVectorStore} is
     * constructed with the {@code EmbeddingModel}, so a wrong choice here is a boot failure.
     */
    @Test
    void theEmbeddingModelStaysLocalWhenGroqIsUsedForChat() {
        runner.withPropertyValues(GROQ_KEY, GROQ_API_KEY_PROPERTY, GROQ_BASE_URL,
                        EMBEDDING_PINNED_LOCAL)
                .run(context -> {
                    assertThat(context).hasSingleBean(EmbeddingModel.class);
                    assertThat(context.getBean(EmbeddingModel.class).getClass().getName())
                            .contains("ollama");
                });
    }

    /**
     * The embedding model is local on the zero-configuration path too — that is the case the
     * portfolio demo runs, and the one where a hosted call would be most surprising.
     */
    @Test
    void theEmbeddingModelStaysLocalWithoutAnApiKey() {
        runner.withPropertyValues(EMBEDDING_PINNED_LOCAL)
                .run(context -> {
                    assertThat(context).hasSingleBean(EmbeddingModel.class);
                    assertThat(context.getBean(EmbeddingModel.class).getClass().getName())
                            .contains("ollama");
                });
    }

    /**
     * Why the embedding selector is pinned in {@code application.yaml} rather than left to the
     * autoconfigurations to negotiate.
     *
     * <p>Without it, and with the OpenAI starter now on the classpath, this context does not
     * start at all: {@code OpenAiEmbeddingAutoConfiguration} is also {@code matchIfMissing = true},
     * so it claims the {@code EmbeddingModel} and then fails at construction with
     * "At least one credential source must be specified". {@code PgVectorStore} is constructed
     * with that bean, so the failure would surface as the application refusing to boot on a
     * machine with no API key — the exact configuration the portfolio demo runs in.
     *
     * <p>The pin turns a credential-dependent startup failure into a one-line, intention-revealing
     * property.
     */
    @Test
    void withoutTheEmbeddingPinTheContextFailsToStartWithoutAnApiKey() {
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
}