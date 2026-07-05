package sg.edu.nus.iss.wellness.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import sg.edu.nus.iss.wellness.dto.ChatDtos;
import sg.edu.nus.iss.wellness.model.AppUser;
import sg.edu.nus.iss.wellness.model.WellnessRecord;
import sg.edu.nus.iss.wellness.repository.AppUserRepository;
import sg.edu.nus.iss.wellness.repository.ChatMessageRepository;
import sg.edu.nus.iss.wellness.repository.WellnessRecordRepository;
import sg.edu.nus.iss.wellness.security.JwtService;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for chat orchestration endpoints.
 * Tests RAG chatbot question/answer flow and chat history retrieval.
 *
 * @author SA62 Team
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Chat Controller Tests")
public class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private ChatMessageRepository chatMessages;

    @Autowired
    private WellnessRecordRepository wellnessRecords;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private AppUser testUser;
    private String token;

    @BeforeEach
    void setUp() {
        testUser = new AppUser();
        testUser.setEmail("alice@wellness.com");
        testUser.setPasswordHash(passwordEncoder.encode("SecurePass123"));
        testUser.setDisplayName("Alice");
        testUser = users.save(testUser);
        token = jwtService.generateToken(testUser);

        seedWellnessRecords();
    }

    private void seedWellnessRecords() {
        for (int i = 0; i < 7; i++) {
            WellnessRecord record = new WellnessRecord();
            record.setUser(testUser);
            record.setRecordDate(LocalDate.now().minusDays(i));
            record.setSleepHours(new BigDecimal("7.5"));
            record.setExerciseType("Running");
            record.setExerciseMinutes(30);
            record.setMoodScore(4);
            wellnessRecords.save(record);
        }
    }

    @Test
    @DisplayName("POST /api/chat/messages - Ask question with valid request")
    void testAskChatQuestion_ValidRequest_ReturnsResponse() throws Exception {
        ChatDtos.ChatRequest request = new ChatDtos.ChatRequest("How can I improve my sleep?");
        String payload = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/chat/messages")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.question").value("How can I improve my sleep?"))
                .andExpect(jsonPath("$.answer").isString())
                .andExpect(jsonPath("$.modelName").isString())
                .andExpect(jsonPath("$.createdAt").isString());
    }

    @Test
    @DisplayName("POST /api/chat/messages - Empty question returns 400")
    void testAskChatQuestion_EmptyQuestion_ReturnsBadRequest() throws Exception {
        ChatDtos.ChatRequest request = new ChatDtos.ChatRequest("");
        String payload = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/chat/messages")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/chat/messages - Null question returns 400")
    void testAskChatQuestion_NullQuestion_ReturnsBadRequest() throws Exception {
        String payload = "{\"question\": null}";

        mockMvc.perform(post("/api/chat/messages")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/chat/messages - Question exceeds max length returns 400")
    void testAskChatQuestion_ExceedsMaxLength_ReturnsBadRequest() throws Exception {
        String longQuestion = "a".repeat(1001);
        ChatDtos.ChatRequest request = new ChatDtos.ChatRequest(longQuestion);
        String payload = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/chat/messages")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/chat/messages - Missing auth token returns 401")
    void testAskChatQuestion_NoAuthToken_ReturnsUnauthorized() throws Exception {
        ChatDtos.ChatRequest request = new ChatDtos.ChatRequest("How can I improve my sleep?");
        String payload = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/chat/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/chat/messages - Retrieve chat history")
    void testListChatHistory_ReturnsUserMessages() throws Exception {
        String question = "What should I eat for better energy?";
        ChatDtos.ChatRequest request = new ChatDtos.ChatRequest(question);
        String payload = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/chat/messages")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/chat/messages")
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].question").value(question))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains(question);
    }

    @Test
    @DisplayName("GET /api/chat/messages - Empty history returns empty array")
    void testListChatHistory_EmptyHistory_ReturnsEmptyArray() throws Exception {
        mockMvc.perform(get("/api/chat/messages")
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/chat/messages - Missing auth token returns 401")
    void testListChatHistory_NoAuthToken_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/chat/messages")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Chat history is user-isolated")
    void testChatHistory_UserIsolation() throws Exception {
        AppUser otherUser = new AppUser();
        otherUser.setEmail("bob@wellness.com");
        otherUser.setPasswordHash(passwordEncoder.encode("SecurePass456"));
        otherUser.setDisplayName("Bob");
        otherUser = users.save(otherUser);
        String otherToken = jwtService.generateToken(otherUser);

        String question = "Only Alice should see this";
        ChatDtos.ChatRequest request = new ChatDtos.ChatRequest(question);
        String payload = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/chat/messages")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/chat/messages")
                .header("Authorization", "Bearer " + otherToken)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Response structure matches API spec")
    void testChatResponse_StructureMatchesSpec() throws Exception {
        ChatDtos.ChatRequest request = new ChatDtos.ChatRequest("Sleep improvement tips?");
        String payload = objectMapper.writeValueAsString(request);

        MvcResult result = mockMvc.perform(post("/api/chat/messages")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains(
                "\"id\":",
                "\"question\":",
                "\"answer\":",
                "\"sources\":",
                "\"modelName\":",
                "\"createdAt\":"
        );
    }
}
