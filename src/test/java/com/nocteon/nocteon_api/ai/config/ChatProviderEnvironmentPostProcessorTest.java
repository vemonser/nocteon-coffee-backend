package com.nocteon.nocteon_api.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

/**
 * The chat provider is chosen from whether {@code GROQ_API_KEY} is set, and this is the only
 * place that decision is made.
 *
 * <p>It has to live in an {@code EnvironmentPostProcessor} because Spring AI 2.0.1 gates each
 * provider's chat autoconfiguration on {@code spring.ai.model.chat} and every provider declares
 * it {@code matchIfMissing = true} — so with both starters on the classpath an unset selector
 * means two {@code ChatModel} beans and injection by type fails. A YAML placeholder cannot
 * express "only when a key is present", and {@code @ConditionalOnProperty} is evaluated too late
 * to compute anything.
 *
 * <p>These tests assert the decision, not the wiring: the resulting bean types are covered by
 * {@code ChatProviderWiringTest}, which builds an actual context.
 */
class ChatProviderEnvironmentPostProcessorTest {

    private final ChatProviderEnvironmentPostProcessor processor =
            new ChatProviderEnvironmentPostProcessor();

    // --- no key: the local model ------------------------------------------------------------

    @Test
    void usesTheLocalModelWhenNoApiKeyIsConfigured() {
        MockEnvironment environment = new MockEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.ai.model.chat"))
                .isEqualTo(ChatProviderEnvironmentPostProcessor.OLLAMA_PROVIDER);
    }

    /**
     * An unset variable in a {@code .env} file reads as an empty string, not as absent. Treating
     * that as "configured" would point the chat model at a provider it cannot authenticate
     * against — the failure would then surface as a 401 on every visitor message.
     */
    @Test
    void treatsABlankApiKeyAsAbsent() {
        MockEnvironment environment = new MockEnvironment().withProperty("GROQ_API_KEY", "");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.ai.model.chat"))
                .isEqualTo(ChatProviderEnvironmentPostProcessor.OLLAMA_PROVIDER);
    }

    /** A key of nothing but whitespace is the same mistake, not a key. */
    @Test
    void treatsAWhitespaceOnlyApiKeyAsAbsent() {
        MockEnvironment environment = new MockEnvironment().withProperty("GROQ_API_KEY", "   ");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.ai.model.chat"))
                .isEqualTo(ChatProviderEnvironmentPostProcessor.OLLAMA_PROVIDER);
    }

    // --- key present: Groq -------------------------------------------------------------------

    @Test
    void usesGroqWhenAnApiKeyIsConfigured() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GROQ_API_KEY", "gsk_not-a-real-key");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.ai.model.chat"))
                .isEqualTo(ChatProviderEnvironmentPostProcessor.OPENAI_PROVIDER);
    }

    // --- explicit override ------------------------------------------------------------------

    /**
     * An operator pinning the provider wins over the derivation. This is what lets a machine
     * that has a key in its environment still run the demo against the local model.
     */
    @Test
    void anExplicitSelectorOverridesTheDerivedValue() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GROQ_API_KEY", "gsk_not-a-real-key")
                .withProperty("spring.ai.model.chat", "ollama");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.ai.model.chat"))
                .isEqualTo(ChatProviderEnvironmentPostProcessor.OLLAMA_PROVIDER);
    }

    /** And the post-processor must not install its property source when it decides nothing. */
    @Test
    void addsNoPropertySourceWhenTheSelectorIsAlreadySet() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.model.chat", "ollama");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getPropertySources()
                .contains(ChatProviderEnvironmentPostProcessor.PROPERTY_SOURCE_NAME))
                .isFalse();
    }

    // --- ordering ----------------------------------------------------------------------------

    /**
     * {@code LOWEST_PRECEDENCE} so this runs after {@code ConfigDataEnvironmentPostProcessor},
     * which is what makes {@code application.yaml} and {@code .env} visible on the environment
     * by the time the decision is made — and what lets an override there be seen.
     */
    @Test
    void runsAfterConfigDataHasBeenLoaded() {
        assertThat(processor.getOrder()).isEqualTo(org.springframework.core.Ordered.LOWEST_PRECEDENCE);
    }
}