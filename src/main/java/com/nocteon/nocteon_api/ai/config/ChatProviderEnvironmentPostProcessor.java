package com.nocteon.nocteon_api.ai.config;

import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * Chooses which provider supplies the chat model, from whether a Groq API key is configured.
 *
 * <h2>Why a post-processor and not a {@code @Bean}</h2>
 * Spring AI 2.0.1 gates each provider's chat autoconfiguration on a selector property, and
 * every one of them is {@code matchIfMissing = true} — verified against
 * {@code OllamaChatAutoConfiguration} and {@code OpenAiChatAutoConfiguration}, both of which
 * carry
 * {@code @ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "<provider>",
 * matchIfMissing = true)}. With both starters on the classpath and the selector unset, both
 * autoconfigurations try to define a {@code ChatModel} and injection by type becomes ambiguous.
 *
 * <p>And the selector cannot be expressed in {@code application.yaml}, because "use the hosted
 * model if and only if a key is present" is a conditional, and a YAML placeholder cannot be
 * conditional. {@code @ConditionalOnProperty} is evaluated while the context is being refreshed,
 * which is too late to compute anything. An {@link EnvironmentPostProcessor} runs before the
 * context exists at all, so it is the one place where the decision can still be made.
 *
 * <p>The alternative — defining our own {@code ChatModel} bean and letting
 * {@code @ConditionalOnMissingBean} on the provider beans back off — does not work here: that
 * annotation is bare, so it backs off only for a bean of the provider's own concrete return
 * type. An {@code OpenAiChatModel} bean of ours would not stop an {@code OllamaChatModel}.
 *
 * <h2>Why this is not an exception path</h2>
 * The fallback is a property value, not a catch block. With no key the selector resolves to
 * {@code ollama}, the local model is wired, and nothing anywhere notices a Groq key is missing.
 * There is no partially-initialised Groq client to fail later, and no branch in
 * {@code AiChatService} — which is why swapping providers required no change to the chat
 * service, its tools, its session store, the controller, or their tests.
 *
 * <h2>Why an explicit override still wins</h2>
 * Setting the selector directly — {@code AI_CHAT_PROVIDER=ollama} or
 * {@code spring.ai.model.chat=...} — takes precedence over the derivation. That is what lets a
 * machine that has a key in its environment still run the demo against the local model, and it
 * keeps the selector inspectable rather than a hidden side effect of reading another property.
 */
public class ChatProviderEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /**
     * The Spring AI selector this post-processor drives.
     *
     * <p>The exact name matters: it is what both provider autoconfigurations test, so writing
     * it to the wrong key would produce two {@code ChatModel} beans rather than one.
     */
    public static final String CHAT_SELECTOR_PROPERTY = "spring.ai.model.chat";

    /** The selector value that activates {@code OpenAiChatAutoConfiguration}. */
    public static final String OPENAI_PROVIDER = "openai";

    /** The selector value that activates {@code OllamaChatAutoConfiguration}. */
    public static final String OLLAMA_PROVIDER = "ollama";

    /**
     * Property source name, exposed so it can be removed or inspected in tests.
     */
    public static final String PROPERTY_SOURCE_NAME = "nocteon-ai-chat-provider";

    private static final String GROQ_API_KEY = "GROQ_API_KEY";

    private static final String GROQ_CHAT_MODEL = "GROQ_CHAT_MODEL";

    /**
     * Model-id fragment marking a reasoning model.
     *
     * <p>Reasoning models return {@code reasoning_content} on every assistant turn. Spring AI's
     * tool-calling loop replays that assistant message verbatim in the follow-up request and Groq
     * rejects it with {@code 400 ... property 'reasoning_content' is unsupported}. The symptom is
     * deceptive: questions that reach a catalog tool answer with the failure apology while
     * questions that do not answer normally, so it presents as an intermittent provider outage
     * rather than a configuration error.
     *
     * <p>It cannot be suppressed from configuration — Spring AI 2.0.1 builds the request with the
     * openai-java client's {@code ChatCompletionCreateParams}, which has no reasoning field, and
     * nothing in {@code spring-ai-openai-2.0.1} serialises {@code reasoning_effort}. Rejecting the
     * model at startup converts a silent, revenue-affecting runtime failure into a startup failure
     * nobody can miss.
     */
    private static final String REASONING_MODEL_MARKER = "gpt-oss";

    /**
     * {@code LOWEST_PRECEDENCE} so this runs after
     * {@code ConfigDataEnvironmentPostProcessor}, which is what makes {@code application.yaml}
     * and {@code .env} visible on the {@link ConfigurableEnvironment} by the time the decision
     * is made — and so an explicit override there is seen rather than overwritten.
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (StringUtils.hasText(environment.getProperty(CHAT_SELECTOR_PROPERTY))) {
            // An operator pinned the provider; honour it rather than second-guessing it.
            return;
        }

        if (!hasGroqApiKey(environment)) {
            environment.getPropertySources().addFirst(new MapPropertySource(
                    PROPERTY_SOURCE_NAME, Map.of(CHAT_SELECTOR_PROPERTY, OLLAMA_PROVIDER)));
            return;
        }

        rejectReasoningModel(environment);

        environment.getPropertySources().addFirst(new MapPropertySource(
                PROPERTY_SOURCE_NAME, Map.of(CHAT_SELECTOR_PROPERTY, OPENAI_PROVIDER)));
    }

    /**
     * Fails startup when a reasoning model is pinned for the hosted chat model.
     *
     * <p>Only enforced when the model is actually coming from {@code GROQ_CHAT_MODEL}, because an
     * unset variable is resolved later by {@code application.yaml} and is therefore invisible here.
     * The shipped default in that file is a non-reasoning model, so this only fires on an
     * explicit override — which is exactly the case worth catching.
     */
    private static void rejectReasoningModel(ConfigurableEnvironment environment) {
        String model = environment.getProperty(GROQ_CHAT_MODEL);
        if (model != null && model.toLowerCase(java.util.Locale.ROOT).contains(REASONING_MODEL_MARKER)) {
            throw new IllegalStateException(
                    GROQ_CHAT_MODEL + " is set to '" + model + "', which is a reasoning model. "
                            + "Reasoning models return reasoning_content on every assistant turn; "
                            + "Spring AI replays that turn inside the tool-calling loop and Groq "
                            + "rejects it with 400 \"property 'reasoning_content' is unsupported\", "
                            + "so every question that reaches a catalog tool returns the failure "
                            + "apology instead of a recommendation. Spring AI 2.0.1 cannot suppress "
                            + "the field, so the model itself has to be non-reasoning — on Groq's "
                            + "free tier that means qwen/qwen3.8-27b.");
        }
    }

    /**
     * Whether a Groq key is configured.
     *
     * <p>Read as {@code GROQ_API_KEY} rather than {@code spring.ai.openai.api-key} because the
     * env var is the thing an operator sets, and because the YAML placeholder that reads it
     * has not necessarily resolved yet at this point in the lifecycle.
     *
     * <p>Blank and whitespace-only both count as absent: an unset variable in a {@code .env}
     * file reads as an empty string, and treating that as "configured" would point the chat
     * model at a provider it cannot authenticate against.
     */
    private static boolean hasGroqApiKey(ConfigurableEnvironment environment) {
        return StringUtils.hasText(environment.getProperty(GROQ_API_KEY));
    }
}