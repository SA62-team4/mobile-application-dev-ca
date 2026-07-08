package sg.edu.nus.iss.wellness.client;

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
import sg.edu.nus.iss.wellness.error.ApiException;

import java.util.Map;
import java.util.function.Consumer;   // (V1 omitted this)

/**
 * HTTP client for the premium weather agent on the local PC, reached through
 * the SSH reverse tunnel (Spring Boot calls http://127.0.0.1:8000 on the Droplet,
 * which the tunnel forwards to the PC). Active only when premium-ai-url is set.
 *
 * @author Tang Chee Seng (with Claude, Gemini)
 */
@Component
public class PremiumAiClient {
    private static final Logger logger = LoggerFactory.getLogger(PremiumAiClient.class);

    private final RestClient restClient;
    private final String premiumAiSecret;
    private final boolean enabled;
    private final ObjectMapper objectMapper;

    public PremiumAiClient(AppProperties properties,
                           RestClient.Builder restClientBuilder,
                           ObjectMapper objectMapper) {
        String url = properties.getPremiumAiUrl();
        this.enabled = url != null && !url.isBlank();
        this.premiumAiSecret = properties.getPremiumAiSecret() != null
                ? properties.getPremiumAiSecret() : "";
        this.objectMapper = objectMapper;
        if (this.enabled) {
            this.restClient = restClientBuilder.clone().baseUrl(url).build();
            logger.info("Premium AI client enabled → {}", url);
        } else {
            this.restClient = null;
            logger.info("Premium AI client disabled (no PREMIUM_AI_URL configured)");
        }
    }

    public boolean isEnabled() { 
        return enabled; 
    }

    /** Blocking call. Returns null on any failure so the caller can fall back. */
    public ChatDtos.AiChatResponse premiumChat(String question, String context, String records,
                                               Double latitude, Double longitude) 
                                               {
        if (!enabled) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Premium AI service is not available right now. Try again later.");
        }
        try {
            var body = new java.util.HashMap<String, Object>();
            body.put("question", question);
            body.put("context", context);
            body.put("records", records);
            body.put("latitude", latitude);
            body.put("longitude", longitude);
            String payload = objectMapper.writeValueAsString(body);
            return restClient.post()
                    .uri("/premium/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Secret", premiumAiSecret)
                    .body(payload)
                    .retrieve()
                    .body(ChatDtos.AiChatResponse.class);
        } catch (RestClientResponseException ex) {
            logger.warn("Premium AI returned {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return null;
        } catch (Exception ex) {
            logger.warn("Premium AI request failed: {}", ex.getMessage());
            return null;
        }
    }

    /** Streaming the chat answer. Invokes onData for each SSE data: payload. Returns false if unreachable. */
    public boolean premiumStreamChat(String question, String context, String records,
                                     Double latitude, Double longitude, Consumer<String> onData) {
        if (!enabled) {
            return false;
        }
        try {
            var body = new java.util.HashMap<String, Object>();
            body.put("question", question);
            body.put("context", context);
            body.put("records", records);
            body.put("latitude", latitude);
            body.put("longitude", longitude);
            String payload = objectMapper.writeValueAsString(body);
            restClient.post()
                    .uri("/premium/chat/stream")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .header("X-Internal-Secret", premiumAiSecret)
                    .body(payload)
                    .exchange((req, resp) -> {
                        if (resp.getStatusCode().isError()) {
                            logger.warn("Premium AI stream returned {}", resp.getStatusCode());
                            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI chatbot service is unavailable");
                        }
                        try (var reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(resp.getBody(), java.nio.charset.StandardCharsets.UTF_8))) {
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
            return true;
        } catch (Exception ex) {
            logger.warn("Premium AI stream failed: {}", ex.getMessage());
            return false;
        }
    }
}