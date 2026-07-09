package sg.edu.nus.iss.wellness.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import sg.edu.nus.iss.wellness.model.AppUser;
import sg.edu.nus.iss.wellness.model.WellnessRecord;
import sg.edu.nus.iss.wellness.repository.AppUserRepository;
import sg.edu.nus.iss.wellness.repository.WellnessRecordRepository;

/**
 * Tests the internal service-to-service endpoints consumed by the Python agent: token
 * enforcement, record retrieval, and recommendation persistence.
 *
 * @author Tiong Zhong Cheng
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Internal Controller Tests")
class InternalControllerTest {

    private static final String TOKEN_HEADER = "X-Internal-Service-Token";
    private static final String VALID_TOKEN = "dev_internal_token";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private WellnessRecordRepository records;

    private AppUser user;

    @BeforeEach
    void setUp() {
        user = new AppUser();
        user.setEmail("agent-target@wellness.com");
        user.setDisplayName("Target");
        user = users.save(user);

        WellnessRecord entry = new WellnessRecord();
        entry.setUser(user);
        entry.setRecordDate(LocalDate.of(2026, Month.JUNE, 1));
        entry.setSleepHours(new BigDecimal("7.5"));
        entry.setExerciseType("run");
        entry.setExerciseMinutes(30);
        entry.setMoodScore(4);
        records.save(entry);
    }

    @Test
    @DisplayName("rejects requests without a valid internal token")
    void rejectsInvalidToken() throws Exception {
        mockMvc.perform(get("/api/internal/users/{id}/wellness-records", user.getId())
                        .header(TOKEN_HEADER, "wrong-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("returns recent wellness records for a valid token")
    void returnsRecentRecords() throws Exception {
        // Wide window so the fixed seed date is always inside it regardless of the run date.
        mockMvc.perform(get("/api/internal/users/{id}/wellness-records", user.getId())
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .param("days", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].exerciseType").value("run"))
                .andExpect(jsonPath("$[0].moodScore").value(4));
    }

    @Test
    @DisplayName("returns 404 when the target user does not exist")
    void unknownUserReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/internal/users/{id}/wellness-records", 999999L)
                        .header(TOKEN_HEADER, VALID_TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("persists a recommendation posted by the agent")
    void savesRecommendation() throws Exception {
        String body = "{\"title\":\"Sleep earlier\",\"trendSummary\":\"late nights\","
                + "\"recommendationText\":\"Wind down by 10pm\",\"actionItems\":[\"no screens\"],"
                + "\"generatedBy\":\"python-agent\"}";

        mockMvc.perform(post("/api/internal/users/{id}/recommendations", user.getId())
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Sleep earlier"))
                .andExpect(jsonPath("$.actionItems[0]").value("no screens"));
    }

    @Test
    @DisplayName("defaults generatedBy to python-agent when omitted")
    void defaultsGeneratedBy() throws Exception {
        String body = "{\"title\":\"Hydrate\",\"trendSummary\":\"low water\","
                + "\"recommendationText\":\"Drink more\",\"actionItems\":[]}";

        mockMvc.perform(post("/api/internal/users/{id}/recommendations", user.getId())
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.generatedBy").value("python-agent"));
    }

    @Test
    @DisplayName("record date is echoed back for a fixed historical entry")
    void echoesHistoricalRecordDate() throws Exception {
        WellnessRecord historical = new WellnessRecord();
        historical.setUser(user);
        historical.setRecordDate(LocalDate.of(2026, Month.JANUARY, 5));
        historical.setSleepHours(new BigDecimal("6.0"));
        historical.setExerciseType("walk");
        historical.setExerciseMinutes(20);
        historical.setMoodScore(3);
        records.save(historical);

        mockMvc.perform(get("/api/internal/users/{id}/wellness-records", user.getId())
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .param("days", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
