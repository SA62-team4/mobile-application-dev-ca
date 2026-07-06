package sg.edu.nus.iss.wellness.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sg.edu.nus.iss.wellness.client.AiServiceClient;
import sg.edu.nus.iss.wellness.dto.ChatDtos;

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
 * @author Zhong Cheng
 */
@DisplayName("Chat Stream Service Tests")
class ChatStreamServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Assembles tokens, forwards frames, and persists the full answer")
    void runStreamAssemblesAndPersists() throws Exception {
        AiServiceClient aiServiceClient = mock(AiServiceClient.class);
        ChatService chatService = mock(ChatService.class);
        ChatStreamService service = new ChatStreamService(aiServiceClient, chatService, objectMapper);
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

        service.runStream(emitter, 1L, "How do I sleep?", List.of());

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
        ChatStreamService service = new ChatStreamService(aiServiceClient, chatService, objectMapper);
        SseEmitter emitter = mock(SseEmitter.class);

        doThrow(new RuntimeException("boom")).when(aiServiceClient).streamChat(any(), any());

        service.runStream(emitter, 1L, "q", List.of());

        verify(chatService, never()).saveStreamedAnswer(any(), any(), any(), any(), any());
        verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
    }
}
