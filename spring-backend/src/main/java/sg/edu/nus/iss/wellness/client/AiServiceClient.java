package sg.edu.nus.iss.wellness.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import sg.edu.nus.iss.wellness.config.AppProperties;
import sg.edu.nus.iss.wellness.dto.ChatDtos;
import sg.edu.nus.iss.wellness.dto.RecommendationDtos;
import sg.edu.nus.iss.wellness.error.ApiException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * HTTP client for the Python RAG and agentic AI service.
 *
 * @author Tiong Zhong Cheng
 */
@Component
public class AiServiceClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiServiceClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AiServiceClient(AppProperties properties, RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder
                .baseUrl(properties.getAiServiceUrl())
                .build();
        this.objectMapper = objectMapper;
    }

    public ChatDtos.AiChatResponse chat(ChatDtos.AiChatRequest request) {
        try {
            String payload = objectMapper.writeValueAsString(request);
            return restClient.post()
                    .uri("/rag/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(ChatDtos.AiChatResponse.class);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("AI chatbot request could not be serialized: {}", exception.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI chatbot service is unavailable");
        } catch (RestClientResponseException exception) {
            LOGGER.warn("AI chatbot service returned {}: {}", exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI chatbot service is unavailable");
        } catch (Exception exception) {
            LOGGER.warn("AI chatbot service request failed: {}", exception.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI chatbot service is unavailable");
        }
    }

    /**
     * Stream a chat answer from the Python RAG service, invoking {@code onData} for each
     * Server-Sent Events {@code data:} payload (the raw JSON string, without the prefix).
     * Retrieval and generation run remotely; this method blocks the calling thread until
     * the upstream stream completes, so callers should run it off the request thread.
     *
     * @param request the AI chat request
     * @param onData  consumer invoked with each SSE data payload as it arrives
     */
    public void streamChat(ChatDtos.AiChatRequest request, Consumer<String> onData) {
        try {
            String payload = objectMapper.writeValueAsString(request);
            restClient.post()
                    .uri("/rag/chat/stream")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .body(payload)
                    .exchange((clientRequest, clientResponse) -> {
                        if (clientResponse.getStatusCode().isError()) {
                            LOGGER.warn("AI chatbot stream returned {}", clientResponse.getStatusCode());
                            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI chatbot service is unavailable");
                        }
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(clientResponse.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.startsWith("data:")) {
                                    String data = line.substring("data:".length()).trim();
                                    if (!data.isEmpty()) {
                                        onData.accept(data);
                                    }
                                }
                            }
                        }
                        return Boolean.TRUE;
                    });
        } catch (JsonProcessingException exception) {
            LOGGER.warn("AI chatbot stream request could not be serialized: {}", exception.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI chatbot service is unavailable");
        } catch (RestClientResponseException exception) {
            LOGGER.warn("AI chatbot stream returned {}: {}", exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI chatbot service is unavailable");
        }
    }

    public RecommendationDtos.RecommendationResponse generateRecommendation(Long userId) {
        try {
            return restClient.post()
                    .uri("/agent/recommendation/{userId}", userId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(RecommendationDtos.RecommendationResponse.class);
        } catch (RestClientResponseException exception) {
            LOGGER.warn("AI recommendation service returned {}: {}", exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI recommendation service is unavailable");
        } catch (Exception exception) {
            LOGGER.warn("AI recommendation service request failed: {}", exception.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI recommendation service is unavailable");
        }
    }
}
