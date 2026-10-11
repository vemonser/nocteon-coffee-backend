package com.nocteon.nocteon_api.ai.controller;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.nocteon.nocteon_api.ai.config.AiProperties;
import com.nocteon.nocteon_api.ai.dto.request.ChatRequest;
import com.nocteon.nocteon_api.ai.dto.response.ChatMessageResponse;
import com.nocteon.nocteon_api.ai.dto.response.ChatResponse;
import com.nocteon.nocteon_api.ai.exception.AiUnavailableException;
import com.nocteon.nocteon_api.ai.service.AiChatService;
import com.nocteon.nocteon_api.ai.service.AiChatSessionStore;
import com.nocteon.nocteon_api.common.dto.ApiResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The chatbot feature flag, across all three endpoints of {@link AiChatController}.
 *
 * <p>The bug this covers: {@code POST /api/ai/chat} correctly raised a 503 while
 * {@code AI_CHATBOT_ENABLED} was false, but {@code GET /history} and {@code DELETE /session}
 * answered anyway. That is worse than an inconsistency. A client asking for history has asked
 * whether the assistant exists, and a 200 with an empty list says "yes, it just has nothing to say"
 * — so the widget stays on screen, pointing at a feature the deployment deliberately switched off,
 * and can still read back a transcript of a conversation that should not exist.
 *
 * <p>Both flag conditions are covered because the service's own check is a conjunction: turning AI
 * off entirely must gate these endpoints too, not just flipping the chatbot switch.
 */
@ExtendWith(MockitoExtension.class)
class AiChatControllerTest {

    @Mock
    private AiChatService aiChatService;

    @Mock
    private AiChatSessionStore sessionStore;

    @Mock
    private AiProperties properties;

    @InjectMocks
    private AiChatController controller;

    private void chatbotOff() {
        lenient().when(properties.isEnabled()).thenReturn(true);
        lenient().when(properties.isChatbotEnabled()).thenReturn(false);
    }

    private void chatbotOn() {
        lenient().when(properties.isEnabled()).thenReturn(true);
        lenient().when(properties.isChatbotEnabled()).thenReturn(true);
    }

    /** {@code ChatRequest} is a bean, not a record, so Jackson binds it the usual way. */
    private static ChatRequest chatRequest() {
        ChatRequest request = new ChatRequest();
        request.setMessage("hello");
        return request;
    }

    // --- POST /api/ai/chat ------------------------------------------------------------------

    @Test
    void chatIsUnavailableWhileTheChatbotIsOff() {
        chatbotOff();

        assertThatThrownBy(() -> controller.chat(chatRequest()))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void chatIsUnavailableWhileAiIsOffEntirely() {
        lenient().when(properties.isEnabled()).thenReturn(false);
        lenient().when(properties.isChatbotEnabled()).thenReturn(true);

        assertThatThrownBy(() -> controller.chat(chatRequest()))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void chatAnswersWhenBothFlagsAreOn() {
        chatbotOn();
        when(aiChatService.chat(anyString(), any(), any()))
                .thenReturn(ChatResponse.builder().reply("hi").products(List.of()).sessionId("s1").build());

        ResponseEntity<ApiResponse<ChatResponse>> response = controller.chat(chatRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getReply()).isEqualTo("hi");
    }

    // --- GET /api/ai/chat/history -----------------------------------------------------------

    @Test
    void historyIsUnavailableWhileTheChatbotIsOff() {
        chatbotOff();

        assertThatThrownBy(() -> controller.history("s1"))
                .isInstanceOf(AiUnavailableException.class);
    }

    /**
     * The specific failure: without the gate, this returns 200 with a transcript from an
     * assistant the deployment switched off.
     */
    @Test
    void historyDoesNotReadTheStoreWhileTheChatbotIsOff() {
        chatbotOff();
        lenient().when(sessionStore.history("s1")).thenReturn(List.of(new UserMessage("hi")));

        assertThatThrownBy(() -> controller.history("s1"))
                .isInstanceOf(AiUnavailableException.class);

        verify(sessionStore, never()).history(anyString());
    }

    @Test
    void historyIsUnavailableWhileAiIsOffEntirely() {
        lenient().when(properties.isEnabled()).thenReturn(false);
        lenient().when(properties.isChatbotEnabled()).thenReturn(true);

        assertThatThrownBy(() -> controller.history("s1"))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void historyReturnsTheStoredConversationWhenTheChatbotIsOn() {
        chatbotOn();
        Message user = new UserMessage("what do you recommend");
        Message assistant = new AssistantMessage("try the yirgacheffe");
        when(sessionStore.history("s1")).thenReturn(List.of(user, assistant));

        ResponseEntity<ApiResponse<List<ChatMessageResponse>>> response = controller.history("s1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData())
                .extracting(ChatMessageResponse::getRole)
                .containsExactly("user", "assistant");
    }

    /** An expired session is empty, not an error — that is the documented normal case. */
    @Test
    void historyIsEmptyWhenTheSessionHasExpired() {
        chatbotOn();
        when(sessionStore.history("gone")).thenReturn(List.of());

        ResponseEntity<ApiResponse<List<ChatMessageResponse>>> response = controller.history("gone");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).isEmpty();
    }

    // --- DELETE /api/ai/chat/session --------------------------------------------------------

    @Test
    void clearIsUnavailableWhileTheChatbotIsOff() {
        chatbotOff();

        assertThatThrownBy(() -> controller.clear("s1"))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void clearDoesNotTouchTheStoreWhileTheChatbotIsOff() {
        chatbotOff();

        assertThatThrownBy(() -> controller.clear("s1"))
                .isInstanceOf(AiUnavailableException.class);

        verify(sessionStore, never()).clear(anyString());
    }

    @Test
    void clearIsUnavailableWhileAiIsOffEntirely() {
        lenient().when(properties.isEnabled()).thenReturn(false);
        lenient().when(properties.isChatbotEnabled()).thenReturn(true);

        assertThatThrownBy(() -> controller.clear("s1"))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void clearForgetsTheSessionWhenTheChatbotIsOn() {
        chatbotOn();

        ResponseEntity<ApiResponse<Void>> response = controller.clear("s1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(sessionStore).clear("s1");
    }

    /** No endpoint may reach a collaborator while the flag is closed. */
    @Test
    void noEndpointTouchesACollaboratorWhileTheChatbotIsOff() {
        chatbotOff();

        assertThatThrownBy(() -> controller.chat(chatRequest())).isInstanceOf(AiUnavailableException.class);
        assertThatThrownBy(() -> controller.history("s1")).isInstanceOf(AiUnavailableException.class);
        assertThatThrownBy(() -> controller.clear("s1")).isInstanceOf(AiUnavailableException.class);

        verifyNoInteractions(aiChatService, sessionStore);
    }
}