package sg.edu.nus.iss.wellness.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sg.edu.nus.iss.wellness.client.AiServiceClient;
import sg.edu.nus.iss.wellness.client.PremiumAiClient;
import sg.edu.nus.iss.wellness.dto.ChatDtos;
import sg.edu.nus.iss.wellness.model.Role;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the streaming chat orchestration: frame parsing, answer/source assembly,
 * persistence hand-off, and error forwarding. Runs {@code runStream} synchronously with a
 * mock emitter so no executor or Spring context is involved.
 *
 * @author Tiong Zhong Cheng
 */
@DisplayName("Chat Stream Service Tests")
class ChatStreamServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Assembles tokens, forwards frames, and persists the full answer")
    void runStreamAssemblesAndPersists() throws Exception {
        AiServiceClient aiServiceClient = mock(AiServiceClient.class);
        ChatService chatService = mock(ChatService.class);
        // Premium disabled so these standard-path tests route to aiServiceClient.
        PremiumAiClient premiumAiClient = mock(PremiumAiClient.class);
        ExerciseIntentClassifier intentClassifier = mock(ExerciseIntentClassifier.class);
        ChatStreamService service = new ChatStreamService(
                aiServiceClient, chatService, objectMapper, premiumAiClient, intentClassifier);
        SseEmitter emitter = mock(SseEmitter.class);

        // Drive the consumer with a canned upstream SSE sequence.
        doAnswer(invocation -> {
            Consumer<String> onData = invocation.getArgument(1);
            onData.accept("{\"type\":\"sources\",\"sources\":[{\"title\":\"Sleep Guide\",\"snippet\":\"snip\"}]}");
            onData.accept("{\"type\":\"token\",\"text\":\"Hello \"}");
            onData.accept("{\"type\":\"token\",\"text\":\"world\"}");
            onData.accept("{\"type\":\"done\",\"modelName\":\"llama3.2:3b\"}");
            return null;
        }).when(aiServiceClient).streamChat(any(), any());

        List<ChatDtos.SourceSnippet> sources = List.of(new ChatDtos.SourceSnippet("Sleep Guide", "snip"));
        when(chatService.saveStreamedAnswer(eq(1L), eq("How do I sleep?"), eq("Hello world"), eq("llama3.2:3b"), any()))
                .thenReturn(new ChatDtos.ChatResponse(
                        5L, "How do I sleep?", "Hello world", sources, "llama3.2:3b", Instant.EPOCH));

        service.runStream(emitter, 1L, Role.USER, "How do I sleep?", List.of(), null, null);

        // The assembled answer, model name, and parsed sources reach persistence.
        verify(chatService).saveStreamedAnswer(1L, "How do I sleep?", "Hello world", "llama3.2:3b", sources);
        // sources + 2 tokens + done => at least four frames forwarded, then a clean completion.
        verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
    }

    @Test
    @DisplayName("Upstream failure forwards an error and does not persist")
    void runStreamHandlesUpstreamFailure() throws Exception {
        AiServiceClient aiServiceClient = mock(AiServiceClient.class);
        ChatService chatService = mock(ChatService.class);
        PremiumAiClient premiumAiClient = mock(PremiumAiClient.class);
        ExerciseIntentClassifier intentClassifier = mock(ExerciseIntentClassifier.class);
        ChatStreamService service = new ChatStreamService(
                aiServiceClient, chatService, objectMapper, premiumAiClient, intentClassifier);
        SseEmitter emitter = mock(SseEmitter.class);

        doThrow(new RuntimeException("boom")).when(aiServiceClient).streamChat(any(), any());

        service.runStream(emitter, 1L, Role.USER, "q", List.of(), null, null);

        verify(chatService, never()).saveStreamedAnswer(any(), any(), any(), any(), any());
        verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
    }


    @Test
    @DisplayName("Premium user + exercise question + node up → uses premium stream, not standard")
    void routesPremiumWhenEligible() throws Exception {
        AiServiceClient ai = mock(AiServiceClient.class);
        ChatService chatService = mock(ChatService.class);
        PremiumAiClient premium = mock(PremiumAiClient.class);
        ExerciseIntentClassifier intent = mock(ExerciseIntentClassifier.class);
        ChatStreamService service = new ChatStreamService(ai, chatService, objectMapper, premium, intent);
        SseEmitter emitter = mock(SseEmitter.class);

        when(premium.isEnabled()).thenReturn(true);
        when(intent.isExerciseRelated("safe to run?")).thenReturn(true);
        when(chatService.buildContextString(any(), any())).thenReturn("ctx");
        when(chatService.formatRecords(any())).thenReturn("recs");
        // Premium stream succeeds and emits a done frame.
        doAnswer(inv -> {
            Consumer<String> onData = inv.getArgument(5);
            onData.accept("{\"type\":\"token\",\"text\":\"Safe.\"}");
            onData.accept("{\"type\":\"done\",\"modelName\":\"gemma4:e4b\"}");
            return true;
        }).when(premium).premiumStreamChat(eq("safe to run?"), any(), any(), any(), any(), any());
        when(chatService.saveStreamedAnswer(any(), any(), any(), any(), any()))
            .thenReturn(new ChatDtos.ChatResponse(1L, "safe to run?", "Safe.", List.of(), "gemma4:e4b", Instant.EPOCH));

        service.runStream(emitter, 1L, Role.PREMIUM_USER, "safe to run?", List.of(), 1.30, 103.85);

        verify(premium).premiumStreamChat(eq("safe to run?"), any(), any(), any(), any(), any());
        verify(ai, never()).streamChat(any(), any());   // standard path NOT used
    }

    @Test
    @DisplayName("Premium node down → falls back to standard stream")
    void fallsBackWhenPremiumFails() throws Exception {
        AiServiceClient ai = mock(AiServiceClient.class);
        ChatService chatService = mock(ChatService.class);
        PremiumAiClient premium = mock(PremiumAiClient.class);
        ExerciseIntentClassifier intent = mock(ExerciseIntentClassifier.class);
        ChatStreamService service = new ChatStreamService(ai, chatService, objectMapper, premium, intent);
        SseEmitter emitter = mock(SseEmitter.class);

        // Eligible for premium, but the premium node is unreachable → returns false.
        when(premium.isEnabled()).thenReturn(true);
        when(intent.isExerciseRelated("safe to run?")).thenReturn(true);
        when(chatService.buildContextString(any(), any())).thenReturn("ctx");
        when(chatService.formatRecords(any())).thenReturn("recs");
        when(premium.premiumStreamChat(any(), any(), any(), any(), any(), any())).thenReturn(false);
        // The standard fallback then drives the stream to a done frame.
        doAnswer(inv -> {
            Consumer<String> onData = inv.getArgument(1);
            onData.accept("{\"type\":\"token\",\"text\":\"Fallback.\"}");
            onData.accept("{\"type\":\"done\",\"modelName\":\"qwen2.5:1.5b\"}");
            return null;
        }).when(ai).streamChat(any(), any());
        when(chatService.saveStreamedAnswer(any(), any(), any(), any(), any()))
            .thenReturn(new ChatDtos.ChatResponse(1L, "safe to run?", "Fallback.", List.of(), "qwen2.5:1.5b", Instant.EPOCH));

        service.runStream(emitter, 1L, Role.PREMIUM_USER, "safe to run?", List.of(), null, null);

        verify(premium).premiumStreamChat(any(), any(), any(), any(), any(), any());  // attempted
        verify(ai).streamChat(any(), any());                                          // fallback engaged
    }

    @Test
    @DisplayName("Standard user is never routed to premium")
    void standardUserBypassesPremium() throws Exception {
        AiServiceClient ai = mock(AiServiceClient.class);
        ChatService chatService = mock(ChatService.class);
        PremiumAiClient premium = mock(PremiumAiClient.class);
        ExerciseIntentClassifier intent = mock(ExerciseIntentClassifier.class);
        ChatStreamService service = new ChatStreamService(ai, chatService, objectMapper, premium, intent);
        SseEmitter emitter = mock(SseEmitter.class);

        // A USER (not PREMIUM_USER) must never reach the premium node, even if it is
        // enabled and the question is exercise-related — role is the first gate.
        when(premium.isEnabled()).thenReturn(true);
        when(intent.isExerciseRelated(any())).thenReturn(true);
        doAnswer(inv -> {
            Consumer<String> onData = inv.getArgument(1);
            onData.accept("{\"type\":\"done\",\"modelName\":\"qwen2.5:1.5b\"}");
            return null;
        }).when(ai).streamChat(any(), any());
        when(chatService.saveStreamedAnswer(any(), any(), any(), any(), any()))
            .thenReturn(new ChatDtos.ChatResponse(1L, "safe to run?", "", List.of(), "qwen2.5:1.5b", Instant.EPOCH));

        service.runStream(emitter, 1L, Role.USER, "safe to run?", List.of(), null, null);

        verify(premium, never()).premiumStreamChat(any(), any(), any(), any(), any(), any());
        verify(ai).streamChat(any(), any());
    }
}
