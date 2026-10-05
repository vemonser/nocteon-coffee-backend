package com.nocteon.nocteon_api.ai.service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.transaction.support.TransactionOperations;

import com.nocteon.nocteon_api.ai.config.AiProperties;
import com.nocteon.nocteon_api.ai.dto.response.ChatResponse;
import com.nocteon.nocteon_api.ai.exception.AiUnavailableException;
import com.nocteon.nocteon_api.brewingMethod.repository.BrewingMethodRepository;
import com.nocteon.nocteon_api.product.dto.response.ProductCardResponse;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.product.mapper.ProductResponseMapper;
import com.nocteon.nocteon_api.product.repository.ProductRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The chatbot is the one AI surface that cannot quietly fall back, because a guess in place of an
 * answer would be a fabricated answer. These tests pin both halves of that: the refusal and
 * no-invention rules live in the system prompt and are asserted here as text, and every failure
 * path degrades to a reply rather than a 500.
 *
 * <p>The service is constructed explicitly rather than through {@code @InjectMocks} because the
 * {@link MessageSource} is the <em>real</em> {@link ResourceBundleMessageSource} over the
 * production bundles. A stubbed message source would let a key missing from the Arabic bundle
 * pass, which is exactly the defect the localized-fallback tests below exist to catch.
 *
 * <p>The per-turn tool object handed to the {@code ChatClient} is real too. Spring AI's
 * {@code MethodToolCallbackProvider} rejects a tool object whose declared methods carry no
 * {@code @Tool} annotation, and a Mockito mock produces exactly such a class — so the wiring is
 * exercised against the real object instead of being stubbed away.
 */
@ExtendWith(MockitoExtension.class)
class AiChatServiceTest {

    private static final String UNAVAILABLE_KEY = "ai.chat.unavailable";
    private static final String EN = "en";
    private static final String AR = "ar";

    @Mock private ChatModel chatModel;
    @Mock private AiChatSessionStore sessionStore;
    @Mock private AiChatFunctions functions;
    @Mock private AiProperties properties;
    @Mock private AiSearchService aiSearchService;
    @Mock private ProductRepository productRepository;
    @Mock private ProductResponseMapper productResponseMapper;
    @Mock private BrewingMethodRepository brewingMethodRepository;
    @Mock private TransactionOperations transactions;

    private MessageSource messageSource;
    private AiChatService service;

    @BeforeEach
    void setUp() {
        messageSource = bundleMessageSource();

        // The real transactional boundary, stubbed to run the work inline — these tests are about
        // what the assistant is shown, not about how the catalog read is wrapped.
        lenient().when(transactions.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback =
                    invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        lenient().when(functions.newTurn()).thenReturn(new AiChatFunctions(
                new CatalogSearch(aiSearchService, productRepository),
                new CoffeeFactSheet(productRepository, transactions),
                new BrewingGuideLookup(brewingMethodRepository),
                new ProductCardBuilder(productRepository, productResponseMapper, transactions)));

        service = new AiChatService(chatModel, sessionStore, functions, properties, messageSource);
    }

    /** The production wiring from {@code LocaleConfig}, verbatim. */
    private static ResourceBundleMessageSource bundleMessageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("messages");
        source.setDefaultEncoding("UTF-8");
        source.setDefaultLocale(Locale.ENGLISH);
        return source;
    }

    private void flags(boolean enabled, boolean chatbotEnabled) {
        lenient().when(properties.isEnabled()).thenReturn(enabled);
        lenient().when(properties.isChatbotEnabled()).thenReturn(chatbotEnabled);
    }

    /**
     * Stubs the model with a finished, tool-call-free answer.
     *
     * <p>{@code getOptions()} is stubbed because Spring AI's own advisors call
     * {@code getOptions().mutate()} while building the tool-calling request. A bare mock returns
     * null there, which would fail every request here for a reason that has nothing to do with the
     * service — the production {@code OllamaChatModel} rejects a null options in its constructor.
     */
    private void modelAnswers(String text) {
        givenModelOptions();
        when(chatModel.call(any(Prompt.class))).thenReturn(
                modelReply(new AssistantMessage(text)));
    }

    private void givenModelOptions() {
        lenient().when(chatModel.getOptions())
                .thenReturn(DefaultToolCallingChatOptions.builder().build());
    }

    private org.springframework.ai.chat.model.ChatResponse modelReply(AssistantMessage message) {
        return new org.springframework.ai.chat.model.ChatResponse(List.of(new Generation(message)));
    }

    // --- disabled -> 503 ---------------------------------------------------------------------

    @Test
    void throwsAiUnavailableWhenMasterSwitchIsOff() {
        flags(false, true);

        assertThatThrownBy(() -> service.chat("hi", null, EN))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void throwsAiUnavailableWhenChatbotDisabled() {
        flags(true, false);

        assertThatThrownBy(() -> service.chat("hi", null, EN))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void disabledChatbotNeverTouchesTheModel() {
        flags(false, false);

        assertThatThrownBy(() -> service.chat("hi", "s1", EN))
                .isInstanceOf(AiUnavailableException.class);

        verify(chatModel, never()).call(any(Prompt.class));
    }

    // --- the happy path ---------------------------------------------------------------------

    @Test
    void returnsReplyAndSessionId() {
        flags(true, true);
        modelAnswers("Hello");

        ChatResponse result = service.chat("What coffee for V60?", "s1", EN);

        assertThat(result.getReply()).isEqualTo("Hello");
        assertThat(result.getSessionId()).isEqualTo("s1");
        assertThat(result.getProducts()).isEmpty();
    }

    @Test
    void generatesSessionIdWhenNoneIsSupplied() {
        flags(true, true);
        modelAnswers("Hello");

        assertThat(service.chat("hi", null, EN).getSessionId()).isNotBlank();
    }

    @Test
    void generatesSessionIdWhenTheSuppliedOneIsBlank() {
        flags(true, true);
        modelAnswers("Hello");

        assertThat(service.chat("hi", "   ", EN).getSessionId()).isNotBlank();
    }

    @Test
    void recordsTurnInSessionStore() {
        flags(true, true);
        modelAnswers("Try Yirgacheffe");

        service.chat("recommend something", "s1", EN);

        verify(sessionStore).append("s1", "recommend something", "Try Yirgacheffe");
    }

    /**
     * The whole function-calling loop, end to end: the model asks for a coffee, the real tool runs
     * against the stubbed catalog, and the product the tool returned becomes the card. This is
     * the test that would fail if the {@code @Tool} wiring, the tool-name mapping or the
     * per-turn slug collector broke — and it is what makes "the model never invents product
     * facts" more than a claim about the prompt.
     */
    @Test
    void aSearchToolCallBecomesTheProductRail() {
        flags(true, true);
        givenModelOptions();
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(modelReply(AssistantMessage.builder()
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                "call-1",
                                "function",
                                "search_products",
                                "{\"query\":\"fruity light roast\",\"limit\":3}")))
                        .build()))
                .thenReturn(modelReply(new AssistantMessage("Try yirgacheffe.")));

        when(aiSearchService.isSearchEnabled()).thenReturn(true);
        when(aiSearchService.semanticCandidates(any(), any(Integer.class)))
                .thenReturn(Map.of(1L, 0.91));
        when(productRepository.findAllByIdInWithDetails(anyList()))
                .thenReturn(List.of(product(1L, "yirgacheffe")));
        when(productRepository.findBySlugIn(List.of("yirgacheffe")))
                .thenReturn(List.of(product(1L, "yirgacheffe")));
        when(productResponseMapper.buildListResponse(any(), eq(EN)))
                .thenReturn(ProductCardResponse.builder().slug("yirgacheffe").build());

        ChatResponse result = service.chat("fruity light roast", "s1", EN);

        assertThat(result.getReply()).isEqualTo("Try yirgacheffe.");
        assertThat(result.getProducts()).extracting(ProductCardResponse::getSlug)
                .containsExactly("yirgacheffe");
    }

    // --- blank input ------------------------------------------------------------------------

    /**
     * {@code @NotBlank} on the request DTO is what rejects a blank message in production; this
     * guard is what keeps a direct service call from spending a model round-trip on nothing.
     */
    @Test
    void blankMessageIsAnsweredWithoutCallingTheModel() {
        flags(true, true);

        ChatResponse result = service.chat("   ", "s1", EN);

        assertThat(result.getReply()).isEmpty();
        assertThat(result.getProducts()).isEmpty();
        assertThat(result.getSessionId()).isEqualTo("s1");
        verify(chatModel, never()).call(any(Prompt.class));
        verify(sessionStore, never()).append(anyString(), anyString(), anyString());
    }

    @Test
    void nullMessageIsAnsweredWithoutCallingTheModel() {
        flags(true, true);

        assertThat(service.chat(null, "s1", EN).getReply()).isEmpty();

        verify(chatModel, never()).call(any(Prompt.class));
    }

    // --- degradation ------------------------------------------------------------------------

    @Test
    void modelFailureDegradesToLocalizedEnglishMessage() {
        flags(true, true);
        givenModelOptions();
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("ollama down"));

        ChatResponse result = service.chat("hi", "s1", EN);

        assertThat(result.getReply())
                .isEqualTo(messageSource.getMessage(UNAVAILABLE_KEY, null, Locale.ENGLISH));
        assertThat(result.getProducts()).isEmpty();
        assertThat(result.getSessionId()).isEqualTo("s1");
    }

    /**
     * The degraded reply is shown to the customer verbatim, so an English-only fallback on the
     * Arabic storefront is a visible defect. Read from the Arabic bundle on purpose: a key missing
     * from {@code messages_ar} fails here.
     */
    @Test
    void modelFailureDegradesToLocalizedArabicMessage() {
        flags(true, true);
        givenModelOptions();
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("ollama down"));

        ChatResponse result = service.chat("مرحبا", "s1", AR);

        assertThat(result.getReply())
                .isEqualTo(messageSource.getMessage(UNAVAILABLE_KEY, null, Locale.forLanguageTag(AR)))
                .isNotEqualTo(messageSource.getMessage(UNAVAILABLE_KEY, null, Locale.ENGLISH));
    }

    /** An empty completion is not an answer; saying so beats rendering a blank bubble. */
    @Test
    void emptyModelAnswerDegradesToTheLocalizedMessage() {
        flags(true, true);
        modelAnswers("");

        ChatResponse result = service.chat("hi", "s1", EN);

        assertThat(result.getReply())
                .isEqualTo(messageSource.getMessage(UNAVAILABLE_KEY, null, Locale.ENGLISH));
    }

    @Test
    void redisOutageDoesNotBreakTheReply() {
        flags(true, true);
        modelAnswers("Hello");
        // The store swallows its own Redis failures; a throw from it here is the defence-in-depth
        // case, and losing the write must still not lose the reply.
        when(sessionStore.history(anyString())).thenThrow(new RuntimeException("redis down"));
        doThrow(new RuntimeException("redis down"))
                .when(sessionStore).append(anyString(), anyString(), anyString());

        assertThat(service.chat("hi", "s1", EN).getReply()).isEqualTo("Hello");
    }

    /**
     * Resolving the cards is a catalog read for display, not part of the answer: losing it costs
     * the shopper the pictures, not the advice.
     */
    @Test
    void failedCardResolutionStillReturnsTheReply() {
        flags(true, true);
        givenModelOptions();
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(modelReply(AssistantMessage.builder()
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                "call-1",
                                "function",
                                "search_products",
                                "{\"query\":\"fruity\",\"limit\":3}")))
                        .build()))
                .thenReturn(modelReply(new AssistantMessage("Try yirgacheffe.")));

        when(aiSearchService.isSearchEnabled()).thenReturn(true);
        when(aiSearchService.semanticCandidates(any(), any(Integer.class)))
                .thenReturn(Map.of(1L, 0.91));
        when(productRepository.findAllByIdInWithDetails(anyList()))
                .thenReturn(List.of(product(1L, "yirgacheffe")));
        when(productRepository.findBySlugIn(anyList()))
                .thenThrow(new RuntimeException("catalog read failed"));

        ChatResponse result = service.chat("fruity", "s1", EN);

        assertThat(result.getReply()).isEqualTo("Try yirgacheffe.");
        assertThat(result.getProducts()).isEmpty();
    }

    // --- the prompt is the hallucination guardrail ---------------------------------------------

    @Test
    void systemPromptForbidsInventingProductData() {
        String prompt = AiChatService.SYSTEM_PROMPT;
        String lower = prompt.toLowerCase(Locale.ROOT);

        assertThat(prompt).containsIgnoringCase("search_products");
        assertThat(prompt).containsIgnoringCase("get_product_details");
        assertThat(lower).contains("never invent");
        assertThat(lower).contains("price");
        assertThat(lower).contains("in stock");
        assertThat(lower).contains("arabic");
    }

    /**
     * There are deliberately no cart, order or wishlist tools, so the refusal has to live in the
     * prompt. If it were dropped the model would either answer from nothing or offer to look an
     * order up — both worse than saying no.
     */
    @Test
    void systemPromptRefusesAccountDataQuestions() {
        String lower = AiChatService.SYSTEM_PROMPT.toLowerCase(Locale.ROOT);

        assertThat(lower).contains("cart");
        assertThat(lower).contains("wishlist");
        assertThat(lower).contains("order");
        assertThat(lower).contains("account");
        assertThat(lower).contains("support");
    }

    /**
     * The prompt names the tools the model is expected to call, and the names Spring AI registers
     * come from {@code @Tool(name = ...)}. Pinning both ends stops a rename from silently leaving
     * the model instructed to call tools that do not exist.
     */
    @Test
    void promptToolNamesMatchTheRegisteredToolNames() {
        Set<String> registered = Arrays.stream(AiChatFunctions.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .map(method -> method.getAnnotation(Tool.class).name())
                .filter(name -> !name.isBlank())
                .collect(Collectors.toSet());

        assertThat(registered).containsExactlyInAnyOrder(
                "search_products", "get_product_details", "get_brewing_guide");
        assertThat(AiChatService.SYSTEM_PROMPT)
                .contains("search_products")
                .contains("get_product_details")
                .contains("get_brewing_guide");
    }

    // --- fixtures ------------------------------------------------------------------------------

    private static Product product(Long id, String slug) {
        return Product.builder()
                .id(id)
                .slug(slug)
                .productType(ProductType.COFFEE)
                .build();
    }
}