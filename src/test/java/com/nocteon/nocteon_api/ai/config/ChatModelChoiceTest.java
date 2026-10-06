package com.nocteon.nocteon_api.ai.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.springframework.mock.env.MockEnvironment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Pins the chat-model choice, which is a correctness constraint rather than a quality preference.
 *
 * <p>The defect this exists for: {@code openai/gpt-oss-20b} is a <em>reasoning</em> model, so it
 * returns {@code reasoning_content} on every assistant turn. Spring AI's tool-calling loop replays
 * that assistant message verbatim in the follow-up request and Groq rejects it —
 * {@code 400 'messages.2' : property 'reasoning_content' is unsupported}. Every question that
 * reached a catalog tool therefore answered with the failure apology while questions that did not
 * call a tool answered normally, which reads as an intermittent failure rather than a broken one.
 *
 * <p>It cannot be fixed by configuration in Spring AI 2.0.1: the request body is built by the
 * openai-java client's {@code ChatCompletionCreateParams}, which has no reasoning field, and no
 * class in {@code spring-ai-openai-2.0.1} serialises {@code reasoning_effort} — so the obvious
 * {@code reasoning-effort: low} property is silently dead config. The only correct answer is a
 * non-reasoning model, and this test is what stops someone switching back on the assumption that
 * it is merely a preference.
 *
 * <p>Uses {@link ApplicationContextRunner} rather than {@code @SpringBootTest} so it needs no
 * database, no pgvector and no API key: the property under test is read from the real
 * {@code application.yaml} that ships, and no network call is made.
 */
class ChatModelChoiceTest {

    private static final String GROQ_KEY_PROPERTY = "GROQ_API_KEY=gsk_not-a-real-key";

    /**
     * The model the shipped {@code application.yaml} configures, read from the real file.
     *
     * <p>Loaded through Spring's own {@code YamlPropertySourceLoader} from the classpath rather
     * than regex-matched out of the text. A regex only proves some scalar exists somewhere in the
     * file: a mis-nested or renamed {@code spring.ai.openai.chat.options.model} block would still
     * satisfy it while wiring nothing, which is the exact mis-nesting this test is supposed to
     * catch. Classpath loading also means it works regardless of the runner's working directory
     * and from a packaged jar.
     */
    private static String shippedChatModel() {
        java.util.List<org.springframework.core.env.PropertySource<?>> sources;
        try {
            sources = new org.springframework.boot.env.YamlPropertySourceLoader()
                    .load("application.yaml",
                            new org.springframework.core.io.ClassPathResource("application.yaml"));
        } catch (java.io.IOException e) {
            throw new AssertionError("cannot load application.yaml from the classpath", e);
        }

        Object value = sources.stream()
                .findFirst()
                .map(source -> source.getProperty("spring.ai.openai.chat.options.model"))
                .orElse(null);
        if (value == null) {
            throw new AssertionError(
                    "application.yaml no longer sets spring.ai.openai.chat.options.model; "
                            + "update this test deliberately");
        }
        return value.toString();
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration.class,
                    org.springframework.ai.model.chat.client.autoconfigure
                            .ChatClientAutoConfiguration.class,
                    org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration.class))
            // The api-key property is read from application.yaml in production; ApplicationContextRunner
            // does not load that file, so it has to be supplied here for the Groq client to build.
            // "not-a-real-key" never leaves the process: no request is made by this test.
            .withPropertyValues(
                    GROQ_KEY_PROPERTY,
                    "spring.ai.openai.api-key=gsk_not-a-real-key",
                    "spring.ai.openai.base-url=https://api.groq.com/openai/v1",
                    // Loaded from the real yaml so a change to what ships cannot pass unnoticed.
            "spring.ai.model.chat=openai",
            "spring.ai.openai.chat.options.model=" + shippedChatModel())
            .withInitializer(context -> new ChatProviderEnvironmentPostProcessor()
                    .postProcessEnvironment(context.getEnvironment(),
                            new org.springframework.boot.SpringApplication()));

    @Test
    @DisplayName("the shipped default chat model is not a reasoning model")
    void theConfiguredChatModelIsNotAReasoningModel() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(OpenAiChatModel.class);
            String model = context.getBean(OpenAiChatModel.class).getDefaultOptions().getModel();

            assertThat(model)
                    .as("""
                            The shipped chat model is '%s'.

                            A reasoning model (anything named gpt-oss-*) returns reasoning_content \
                            on every assistant turn. Spring AI replays that turn inside the \
                            tool-calling loop and Groq answers 400 'property reasoning_content is \
                            unsupported', so every question that reaches a catalog tool returns \
                            the failure apology instead of a recommendation.

                            Spring AI 2.0.1 cannot suppress the field, so the model choice itself \
                            is the only fix.""", model)
                    .doesNotContain("gpt-oss");
        });
    }

    @Test
    @DisplayName("the shipped default is the free-tier model whose tool loop completes")
    void theConfiguredChatModelIsTheVerifiedFreeTierChoice() {
        runner.run(context -> {
            String model = context.getBean(OpenAiChatModel.class).getDefaultOptions().getModel();

            assertThat(model)
                    .as("""
                            The chat model is '%s'.

                            Verified live against Groq's free tier: qwen/qwen3.8-27b completes a \
                            full two-step tool round trip. llama-3.1-8b-instant and \
                            llama-3.3-70b-versatile return 'does not exist or you do not have \
                            access' on a free account, and both gpt-oss models are reasoning \
                            models — so this is the only option that works without a paid plan.""",
                            model)
                    .isEqualTo("qwen/qwen3.8-27b");
        });
    }

    @Test
    @DisplayName("pinning a reasoning model fails startup instead of degrading silently")
    void aReasoningModelIsRejectedAtStartup() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GROQ_API_KEY", "gsk_not-a-real-key")
                .withProperty("GROQ_CHAT_MODEL", "openai/gpt-oss-20b");

        assertThatIllegalStateException()
                .as("A reasoning model must be rejected at startup. Left unchecked it produces a "
                        + "chatbot that answers every tool-backed question with the failure "
                        + "apology, which reads as an intermittent provider outage and is found "
                        + "only by customers.")
                .isThrownBy(() -> new ChatProviderEnvironmentPostProcessor()
                        .postProcessEnvironment(environment, new org.springframework.boot.SpringApplication()))
                .withMessageContaining("reasoning model");
    }

    @Test
    @DisplayName("the local fallback is unaffected by the reasoning-model guard")
    void theLocalFallbackIsUnaffected() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GROQ_CHAT_MODEL", "openai/gpt-oss-20b");

        new ChatProviderEnvironmentPostProcessor()
                .postProcessEnvironment(environment, new org.springframework.boot.SpringApplication());

        assertThat(environment.getProperty(ChatProviderEnvironmentPostProcessor.CHAT_SELECTOR_PROPERTY))
                .as("Without a Groq key the model variable is irrelevant; the guard must not stop "
                        + "the zero-API-key path from booting on the local model.")
                .isEqualTo(ChatProviderEnvironmentPostProcessor.OLLAMA_PROVIDER);
    }
}