package sg.edu.nus.iss.wellness.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import sg.edu.nus.iss.wellness.client.AiServiceClient;
import sg.edu.nus.iss.wellness.dto.RecommendationDtos;
import sg.edu.nus.iss.wellness.model.AppUser;
import sg.edu.nus.iss.wellness.model.Recommendation;
import sg.edu.nus.iss.wellness.repository.AppUserRepository;
import sg.edu.nus.iss.wellness.repository.RecommendationRepository;
import sg.edu.nus.iss.wellness.security.JwtService;

/**
 * Ownership tests for recommendation endpoints (T-204, NFR-01): a user must never see
 * another user's saved recommendations.
 *
 * @author SA62 Team
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Recommendation Controller Tests")
class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private RecommendationRepository recommendations;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    // Stubbed so tests never call the external Python agent service (which would otherwise return 503).
    @MockBean
    private AiServiceClient aiServiceClient;

    private AppUser testUser;
    private AppUser otherUser;
    private String token;
    private String otherToken;

    @BeforeEach
    void setUp() {
        testUser = new AppUser();
        testUser.setEmail("alice@wellness.com");
        testUser.setPasswordHash(passwordEncoder.encode("SecurePass123"));
        testUser.setDisplayName("Alice");
        testUser = users.save(testUser);
        token = jwtService.generateToken(testUser);

        otherUser = new AppUser();
        otherUser.setEmail("bob@wellness.com");
        otherUser.setPasswordHash(passwordEncoder.encode("SecurePass456"));
        otherUser.setDisplayName("Bob");
        otherUser = users.save(otherUser);
        otherToken = jwtService.generateToken(otherUser);
    }

    private Recommendation seedRecommendation(AppUser owner, String title) {
        Recommendation recommendation = new Recommendation();
        recommendation.setUser(owner);
        recommendation.setTitle(title);
        recommendation.setTrendSummary("Sleep trending down this week");
        recommendation.setRecommendationText("Try winding down earlier in the evening");
        recommendation.setActionItems("Sleep by 11pm\nAvoid screens after 10pm");
        recommendation.setGeneratedBy("python-agent");
        return recommendations.save(recommendation);
    }

    @Test
    @DisplayName("GET /api/recommendations - Only returns the caller's own recommendations")
    void list_onlyReturnsOwnRecommendations() throws Exception {
        seedRecommendation(testUser, "Alice's recommendation");
        seedRecommendation(otherUser, "Bob's recommendation");

        mockMvc.perform(get("/api/recommendations")
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Alice's recommendation"));

        mockMvc.perform(get("/api/recommendations")
                .header("Authorization", "Bearer " + otherToken)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Bob's recommendation"));
    }

    @Test
    @DisplayName("GET /api/recommendations - Missing auth token returns 401")
    void list_noAuthToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/recommendations")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/recommendations/generate - Generates for the authenticated user only")
    void generate_usesAuthenticatedUsersId() throws Exception {
        when(aiServiceClient.generateRecommendation(testUser.getId())).thenReturn(
                new RecommendationDtos.RecommendationResponse(
                        1L, "Generated title", "Trend summary", "Recommendation text",
                        List.of("Do this", "Do that"), "python-agent", java.time.Instant.now()));

        mockMvc.perform(post("/api/recommendations/generate")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Generated title"));
    }

    @Test
    @DisplayName("POST /api/recommendations/generate - Missing auth token returns 401")
    void generate_noAuthToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/recommendations/generate"))
                .andExpect(status().isUnauthorized());
    }
}
