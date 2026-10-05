package com.nocteon.nocteon_api.ai.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.nocteon.nocteon_api.ai.config.AiProperties;
import com.nocteon.nocteon_api.ai.dto.response.ChatResponse;
import com.nocteon.nocteon_api.ai.exception.AiUnavailableException;
import com.nocteon.nocteon_api.product.dto.response.ProductCardResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The shopping assistant: one model call, three tools, and no ability to make a fact up.
 *
 * <h2>Why this is the one AI surface that answers rather than degrades</h2>
 * {@code /products/{slug}/similar} can fall back to attribute similarity because a wrong
 * suggestion is a bad recommendation. A chatbot cannot: the plausible middle of "what's in my
 * cart", "how much is my order" and "is the Sidamo in stock" is a confident, fluent, invented
 * answer, and a storefront that produces one on demand is worse than one that admits it is off.
 * So this service answers only from tool output, and the endpoint is a 503 while the feature is
 * off rather than a guess.
 *
 * <p>Once a reply <em>is</em> being produced the calculus flips: the visitor has already given us
 * a turn, so failing the whole request would discard real work over a transient error. Every
 * failure from that point on — an unreachable model, an unreadable history, a failed catalog read
 * — degrades to a reply with no product rail, and never to a 500.
 *
 * <h2>Where the guarantees actually live</h2>
 * {@link #SYSTEM_PROMPT} states the rules, but {@link AiChatFunctions} enforces them: the model
 * holds no tool that reaches a cart or an order, and no way to state a price that was not read
 * from the database. The prompt tells it what to do; the tool surface makes anything else
 * impossible.
 *
 * <h2>Why memory is loaded and stored by hand</h2>
 * Spring AI 2.0.1 offers {@code MessageChatMemoryAdvisor}, but wiring it would introduce a second
 * store competing with {@link AiChatSessionStore} for the same responsibility, with its own idea
 * of how long a conversation lives. Reading the window in and writing the turn out keeps one
 * authority over conversation state and one place where the TTL is applied. It also means an
 * unreachable Redis costs the assistant its memory rather than its answer — the prompt simply
 * goes out without the earlier turns.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiChatService {

    /**
     * The assistant's instructions.
     *
     * <p>Public and pinned by tests because it is the specification of what the model may do, not
     * an implementation detail: a rewrite that quietly dropped the no-invention rule or the
     * account-data refusal would leave every other guarantee intact while defeating the point of
     * the feature, and no other test would notice.
     *
     * <p>The tool names are spelled exactly as {@code @Tool(name = ...)} registers them. A rename
     * on either side would leave the model calling tools that do not exist, and
     * {@code promptToolNamesMatchTheRegisteredToolNames} fails.
     */
    public static final String SYSTEM_PROMPT = """
            You are the Nocteon coffee specialist: an experienced barista and roaster for a \
            specialty coffee shop. You help one visitor at a time choose coffee they will enjoy.

            HOW TO ANSWER
            - If you do not know how they like their coffee, ask exactly one clarifying question \
            first: roast, flavour or brewing method.
            - Recommend two or three coffees. For each, one short clause on why it fits what they \
            asked for.
            - Keep replies under about 120 words. Warm and specific, no marketing filler.

            FACTS — THIS IS THE MOST IMPORTANT RULE
            - Every product fact must come from a tool. Call search_products to find coffees and \
            get_product_details to read their origin, roast, process, variety, tasting notes, \
            price and availability. Call get_brewing_guide for brewing instructions.
            - Never invent a coffee, an origin, a tasting note, a price, a discount or whether \
            something is in stock. If a tool did not tell you, you do not know it.
            - If a tool returns no matches, say that you could not find one. Do not suggest \
            something else from memory.
            - Quote product slugs verbatim when you name a coffee. Do not rename or translate them.
            - When you give a price, give exactly the figure the tool returned.

            WHAT YOU CANNOT DO
            - You have no access to customer accounts. If asked about a cart, wishlist, order, \
            delivery, address, payment or any other account data, say plainly that you cannot see \
            it and point the visitor to support. Do not guess, and do not offer to look it up.
            - You do not take orders, change orders or issue refunds.

            SCOPE AND LANGUAGE
            - Reply in the language the visitor wrote in. If they write in Arabic, reply in Arabic; \
            the same rules and the same tools apply.
            - Decline anything unrelated to coffee in one friendly line, then offer what you can \
            help with.""";

    private static final String UNAVAILABLE_REPLY_KEY = "ai.chat.unavailable";

    private final ChatModel chatModel;
    private final AiChatSessionStore sessionStore;
    private final AiChatFunctions functions;
    private final AiProperties properties;
    private final MessageSource messageSource;

    /**
     * Answers one message.
     *
     * @param message   what the visitor typed; a blank value answers with an empty reply rather
     *                  than spending a model round-trip on nothing
     * @param sessionId the conversation handle, or blank to start a new conversation
     * @param locale    BCP-47 tag from the client, or blank to use the request's locale
     * @return the reply, the product rail it cites, and the handle to send next time
     * @throws AiUnavailableException when the chatbot is switched off, or when AI is off entirely —
     *                                never degraded into a guess
     */
    public ChatResponse chat(String message, String sessionId, String locale) {
        if (!properties.isEnabled() || !properties.isChatbotEnabled()) {
            throw new AiUnavailableException();
        }

        String effectiveSessionId = StringUtils.hasText(sessionId)
                ? sessionId.trim()
                : UUID.randomUUID().toString();
        Locale resolvedLocale = resolveLocale(locale);

        if (!StringUtils.hasText(message)) {
            // @NotBlank on the request DTO is what rejects this in production; the guard is what
            // stops a direct service call from spending a model round-trip on an empty question.
            return ChatResponse.builder()
                    .reply("")
                    .products(List.of())
                    .sessionId(effectiveSessionId)
                    .build();
        }

        // Minted per turn, not shared: the tool object is what remembers which coffees this reply
        // cited, and two concurrent visitors must not see each other's answers.
        AiChatFunctions turn = functions.newTurn();

        String reply = generate(message, effectiveSessionId, turn, resolvedLocale);

        rememberTurn(effectiveSessionId, message, reply);

        return ChatResponse.builder()
                .reply(reply)
                .products(cards(turn, resolvedLocale))
                .sessionId(effectiveSessionId)
                .build();
    }

    /**
     * The single model call, with history in and a localized apology out.
     *
     * <p>The catch is deliberately around the whole call, because every way it can fail is an
     * infrastructure failure rather than a bad question: a downed Ollama, a refused connection, a
     * malformed tool argument. None of them are the visitor's fault, and none of them deserve a
     * 500 on a page they are already looking at.
     */
    private String generate(String message, String sessionId, AiChatFunctions turn, Locale locale) {
        try {
            String content = client(turn).prompt()
                    .messages(history(sessionId))
                    .user(message)
                    .call()
                    .content();

            // An empty completion is not an answer. Saying so beats rendering a blank bubble.
            return StringUtils.hasText(content) ? content : unavailableReply(locale);
        } catch (Exception e) {
            log.warn("Coffee assistant unavailable, replying without an answer: {}", e.getMessage());
            return unavailableReply(locale);
        }
    }

    /**
     * Records the turn, tolerating a store that will not take it.
     *
     * <p>Guarded separately from {@link #history} because the reply already exists by this point:
     * throwing away a generated answer because the memory could not be written is exactly the
     * trade this service refuses to make. {@code AiChatSessionStore} swallows its own Redis
     * failures, so this is the second line rather than the first.
     */
    private void rememberTurn(String sessionId, String message, String reply) {
        try {
            sessionStore.append(sessionId, message, reply);
        } catch (Exception e) {
            log.warn("Chat turn not stored for session {}, continuing without it: {}",
                    redact(sessionId), e.getMessage());
        }
    }

    /** Keeps a caller-chosen session handle out of the log, since the caller chose it. */
    private static String redact(String sessionId) {
        String trimmed = sessionId == null ? "" : sessionId.trim();
        return trimmed.length() <= 4 ? "****" : "****" + trimmed.substring(trimmed.length() - 4);
    }

    /**
     * The client for one turn.
     *
     * <p>Built per request because the tool object is per turn. The construction is cheap —
     * reflection over three methods — and Spring AI reads the {@code @Tool} annotations at build
     * time, so a tool class that lost them fails loudly here instead of silently answering a
     * product question with no catalog access at all.
     */
    private ChatClient client(AiChatFunctions turn) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(turn)
                .build();
    }

    /**
     * The earlier turns, or nothing.
     *
     * <p>Swallows a store failure so a Redis outage costs memory rather than the answer. The
     * {@code SYSTEM} role is dropped rather than replayed: the system prompt is already on the
     * request, and a second one from history would silently contradict it the day the prompt
     * changes.
     */
    private List<Message> history(String sessionId) {
        try {
            return sessionStore.history(sessionId).stream()
                    .filter(message -> message.getMessageType() != MessageType.SYSTEM)
                    .toList();
        } catch (Exception e) {
            log.warn("Chat history unavailable, answering without it: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * The product rail, or none.
     *
     * <p>A separate boundary from the model call: the rail is a catalog read for display, and
     * losing it costs the shopper the pictures, not the advice.
     */
    private List<ProductCardResponse> cards(AiChatFunctions turn, Locale locale) {
        try {
            return turn.cardsFor(turn.citedSlugs(), locale.getLanguage());
        } catch (Exception e) {
            log.warn("Product cards unavailable, replying without them: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * The visitor's language, from the client when it says and from {@code Accept-Language}
     * otherwise.
     *
     * <p>A client that sends nothing still gets the storefront's language, so the field is a hint
     * rather than a switch. An unparseable tag falls back the same way instead of pinning the
     * reply to an arbitrary language.
     */
    private Locale resolveLocale(String locale) {
        if (StringUtils.hasText(locale)) {
            Locale parsed = StringUtils.parseLocale(locale.trim());
            if (parsed != null && StringUtils.hasText(parsed.getLanguage())) {
                return parsed;
            }
        }
        return LocaleContextHolder.getLocale();
    }

    /** What the visitor reads when the assistant could not answer. */
    private String unavailableReply(Locale locale) {
        return messageSource.getMessage(UNAVAILABLE_REPLY_KEY, null, locale);
    }
}