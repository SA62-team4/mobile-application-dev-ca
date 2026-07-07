package sg.edu.nus.iss.wellness.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sg.edu.nus.iss.wellness.client.AiServiceClient;
import sg.edu.nus.iss.wellness.dto.ChatDtos;
import sg.edu.nus.iss.wellness.model.AppUser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Orchestrates streamed chatbot responses over Server-Sent Events.
 *
 * <p>The Python RAG service streams tokens as they are generated; this service proxies that
 * stream to the Android client through an {@link SseEmitter} and, once the stream completes,
 * persists the assembled exchange so chat history stays consistent with the blocking path.
 *
 * <p>Wellness context is resolved on the request thread (where the JPA session and security
 * context are available) and the actual streaming runs on a background worker so the servlet
 * thread is released promptly.
 *
 * @author Zhong Cheng
 */
@Service
public class ChatStreamService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatStreamService.class);
    // Generous timeout: CPU-only Ollama generation can take well over a minute.
    private static final long STREAM_TIMEOUT_MS = 180_000L;
    private static final String SOURCES_FIELD = "sources";

    private final AiServiceClient aiServiceClient;
    private final ChatService chatService;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatStreamService(AiServiceClient aiServiceClient,
                             ChatService chatService,
                             ObjectMapper objectMapper) {
        this.aiServiceClient = aiServiceClient;
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    /**
     * Start streaming an answer for the given question.
     *
     * @param user     the authenticated user
     * @param question the wellness question
     * @return an emitter that streams {@code sources}, {@code token}, and a terminal
     *         {@code done} (or {@code error}) SSE data frame
     */
    public SseEmitter stream(AppUser user, String question) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        Long userId = user.getId();
        List<ChatDtos.RecentRecord> records = chatService.fetchRecentWellnessRecords(user);
        executor.execute(() -> runStream(emitter, userId, question, records));
        return emitter;
    }

    // Package-private so the streaming/persistence logic can be unit-tested synchronously
    // with a mock emitter, without going through the executor.
    void runStream(SseEmitter emitter, Long userId, String question, List<ChatDtos.RecentRecord> records) {
        StringBuilder answer = new StringBuilder();
        List<ChatDtos.SourceSnippet> sources = new ArrayList<>();
        StringBuilder modelName = new StringBuilder();
        try {
            aiServiceClient.streamChat(
                    new ChatDtos.AiChatRequest(userId, question, records),
                    data -> handleFrame(emitter, data, answer, sources, modelName)
            );
            ChatDtos.ChatResponse saved = chatService.saveStreamedAnswer(
                    userId, question, answer.toString().trim(), modelName.toString(), sources);
            sendDone(emitter, saved);
            emitter.complete();
        } catch (StreamForwardedException forwarded) {
            // An upstream error frame was already relayed to the client; just close cleanly.
            emitter.complete();
        } catch (Exception exception) {
            LOGGER.warn("Chat stream failed for user {}: {}", userId, exception.getMessage());
            sendError(emitter, "Chatbot unavailable. Please retry when services are running.");
            emitter.complete();
        }
    }

    private void handleFrame(SseEmitter emitter,
                             String data,
                             StringBuilder answer,
                             List<ChatDtos.SourceSnippet> sources,
                             StringBuilder modelName) {
        try {
            JsonNode node = objectMapper.readTree(data);
            switch (node.path("type").asText()) {
                case SOURCES_FIELD -> {
                    for (JsonNode source : node.path(SOURCES_FIELD)) {
                        sources.add(new ChatDtos.SourceSnippet(
                                source.path("title").asText(), source.path("snippet").asText()));
                    }
                    emitter.send(SseEmitter.event().data(data));
                }
                case "token" -> {
                    answer.append(node.path("text").asText());
                    emitter.send(SseEmitter.event().data(data));
                }
                case "done" -> modelName.append(node.path("modelName").asText());
                case "error" -> {
                    emitter.send(SseEmitter.event().data(data));
                    throw new StreamForwardedException();
                }
                default -> LOGGER.debug("Ignoring unknown chat stream frame: {}", data);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void sendDone(SseEmitter emitter, ChatDtos.ChatResponse saved) throws IOException {
        var payload = objectMapper.createObjectNode();
        payload.put("type", "done");
        payload.put("id", saved.id());
        payload.put("modelName", saved.modelName());
        payload.put("createdAt", saved.createdAt() == null ? null : saved.createdAt().toString());
        payload.set(SOURCES_FIELD, objectMapper.valueToTree(saved.sources()));
        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            var payload = objectMapper.createObjectNode();
            payload.put("type", "error");
            payload.put("message", message);
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
        } catch (IOException exception) {
            LOGGER.debug("Could not send error frame (client likely disconnected): {}", exception.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    /** Signals that an upstream error frame was already forwarded to the client. */
    private static final class StreamForwardedException extends RuntimeException {
    }
}
