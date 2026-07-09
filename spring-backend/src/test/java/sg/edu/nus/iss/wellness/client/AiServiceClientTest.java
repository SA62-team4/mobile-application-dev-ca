package sg.edu.nus.iss.wellness.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import sg.edu.nus.iss.wellness.config.AppProperties;
import sg.edu.nus.iss.wellness.dto.ChatDtos;
import sg.edu.nus.iss.wellness.dto.RecommendationDtos;
import sg.edu.nus.iss.wellness.error.ApiException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.http.HttpMethod;

/**
 * Unit tests for {@link AiServiceClient} covering the RAG chat, streaming, and recommendation
 * calls plus their failure translation into {@link ApiException}. The RestClient is backed by
 * {@link MockRestServiceServer} so no real Python service is required.
 *
 * @author Tiong Zhong Cheng
 */
@DisplayName("AI Service Client Tests")
class AiServiceClientTest {

    private static final String BASE_URL = "http://localhost:8000";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private ObjectMapper objectMapper;
    private AiServiceClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        objectMapper = new ObjectMapper();
        AppProperties properties = new AppProperties();
        properties.setAiServiceUrl(BASE_URL);
        client = new AiServiceClient(properties, builder, objectMapper);
    }

    private ChatDtos.AiChatRequest sampleRequest() {
        return new ChatDtos.AiChatRequest(1L, "How do I sleep better?", List.of());
    }

    @Test
    @DisplayName("chat() returns the parsed RAG answer on success")
    void chatReturnsAnswer() {
        server.expect(requestTo(BASE_URL + "/rag/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"answer\":\"Sleep 8 hours\",\"sources\":[{\"title\":\"Guide\",\"snippet\":\"rest\"}],"
                                + "\"modelName\":\"llama3.2:3b\"}",
                        MediaType.APPLICATION_JSON));

        ChatDtos.AiChatResponse response = client.chat(sampleRequest());

        assertThat(response.answer()).isEqualTo("Sleep 8 hours");
        assertThat(response.modelName()).isEqualTo("llama3.2:3b");
        assertThat(response.sources()).singleElement()
                .satisfies(source -> assertThat(source.title()).isEqualTo("Guide"));
        server.verify();
    }

    @Test
    @DisplayName("chat() maps an upstream error response to a 503 ApiException")
    void chatMapsErrorResponse() {
        server.expect(requestTo(BASE_URL + "/rag/chat"))
                .andRespond(withServerError());
        ChatDtos.AiChatRequest request = sampleRequest();

        assertThatThrownBy(() -> client.chat(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    @DisplayName("chat() maps a serialization failure to a 503 ApiException")
    void chatMapsSerializationFailure() throws Exception {
        ObjectMapper failing = mock(ObjectMapper.class);
        when(failing.writeValueAsString(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new JsonProcessingException("boom") {
                });
        AppProperties properties = new AppProperties();
        properties.setAiServiceUrl(BASE_URL);
        AiServiceClient failingClient = new AiServiceClient(properties, RestClient.builder(), failing);
        ChatDtos.AiChatRequest request = sampleRequest();

        assertThatThrownBy(() -> failingClient.chat(request))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("streamChat() forwards each non-empty SSE data payload to the consumer")
    void streamChatForwardsDataFrames() {
        String sse = """
                data: {"type":"token","text":"Hi"}

                data:
                data: {"type":"done"}
                """;
        server.expect(requestTo(BASE_URL + "/rag/chat/stream"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(sse, MediaType.TEXT_EVENT_STREAM));

        List<String> received = new ArrayList<>();
        client.streamChat(sampleRequest(), received::add);

        assertThat(received).containsExactly(
                "{\"type\":\"token\",\"text\":\"Hi\"}",
                "{\"type\":\"done\"}");
        server.verify();
    }

    @Test
    @DisplayName("streamChat() maps an upstream error status to a 503 ApiException")
    void streamChatMapsErrorStatus() {
        server.expect(requestTo(BASE_URL + "/rag/chat/stream"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        ChatDtos.AiChatRequest request = sampleRequest();

        assertThatThrownBy(() -> client.streamChat(request, data -> { }))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("generateRecommendation() returns the parsed recommendation on success")
    void generateRecommendationReturnsResponse() {
        server.expect(requestTo(BASE_URL + "/agent/recommendation/1"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"id\":7,\"title\":\"Rest more\",\"trendSummary\":\"low sleep\","
                                + "\"recommendationText\":\"Aim for 8h\",\"actionItems\":[\"sleep\"],"
                                + "\"generatedBy\":\"python-agent\",\"createdAt\":\"2026-01-01T00:00:00Z\"}",
                        MediaType.APPLICATION_JSON));

        RecommendationDtos.RecommendationResponse response = client.generateRecommendation(1L);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.title()).isEqualTo("Rest more");
        assertThat(response.actionItems()).containsExactly("sleep");
        server.verify();
    }

    @Test
    @DisplayName("generateRecommendation() maps an upstream error to a 503 ApiException")
    void generateRecommendationMapsError() {
        server.expect(requestTo(BASE_URL + "/agent/recommendation/1"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.generateRecommendation(1L))
                .isInstanceOf(ApiException.class);
    }
}
