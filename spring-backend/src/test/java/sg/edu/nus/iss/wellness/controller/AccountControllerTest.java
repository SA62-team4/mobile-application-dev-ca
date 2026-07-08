package sg.edu.nus.iss.wellness.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import sg.edu.nus.iss.wellness.dto.AccountDtos;
import sg.edu.nus.iss.wellness.dto.AuthDtos;
import sg.edu.nus.iss.wellness.model.AppUser;
import sg.edu.nus.iss.wellness.model.ChatMessage;
import sg.edu.nus.iss.wellness.model.Recommendation;
import sg.edu.nus.iss.wellness.model.WellnessRecord;
import sg.edu.nus.iss.wellness.repository.AppUserRepository;
import sg.edu.nus.iss.wellness.repository.ChatMessageRepository;
import sg.edu.nus.iss.wellness.repository.RecommendationRepository;
import sg.edu.nus.iss.wellness.repository.WellnessRecordRepository;
import sg.edu.nus.iss.wellness.security.JwtService;

/**
 * Integration tests for the privacy / data-control endpoints (S-03):
 * export, reversible deactivate + reactivate, and permanent password-confirmed
 * delete.
 *
 * @author Chua Wei Yi Justin, Tiong Zhong Cheng
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AppUserRepository users;
    @Autowired private WellnessRecordRepository wellnessRecords;
    @Autowired private RecommendationRepository recommendations;
    @Autowired private ChatMessageRepository chatMessages;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String PASSWORD = "Secret123";

    private AppUser user;
    private String token;

    @BeforeEach
    void setUp() {
        user = new AppUser();
        user.setEmail("dana@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setDisplayName("Dana");
        user.setHeightCm(new BigDecimal("170.0"));
        user = users.save(user);
        token = jwtService.generateToken(user);
    }

    private WellnessRecord seedRecord(AppUser owner) {
        WellnessRecord entry = new WellnessRecord();
        entry.setUser(owner);
        entry.setRecordDate(LocalDate.of(2026, Month.JULY, 1));
        entry.setSleepHours(new BigDecimal("7.0"));
        entry.setExerciseType("Walking");
        entry.setExerciseMinutes(30);
        entry.setMoodScore(4);
        entry.setNotes("ok");
        return wellnessRecords.save(entry);
    }

    private Recommendation seedRecommendation(AppUser owner) {
        Recommendation rec = new Recommendation();
        rec.setUser(owner);
        rec.setTitle("Sleep plan");
        rec.setTrendSummary("Sleep was low.");
        rec.setRecommendationText("Wind down earlier.");
        rec.setActionItems("Set bedtime\nDim lights\nLog tomorrow");
        rec.setGeneratedBy("python-agent");
        return recommendations.save(rec);
    }

    private ChatMessage seedChat(AppUser owner) {
        ChatMessage message = new ChatMessage();
        message.setUser(owner);
        message.setUserQuestion("How do I sleep better?");
        message.setAssistantAnswer("Keep a steady bedtime.");
        message.setModelName("qwen2.5:1.5b");
        return chatMessages.save(message);
    }

    private AppUser seedOtherUser() {
        AppUser other = new AppUser();
        other.setEmail("evan@example.com");
        other.setPasswordHash(passwordEncoder.encode("OtherPw123"));
        other.setDisplayName("Evan");
        return users.save(other);
    }

    private AppUser seedGoogleOnlyUser() {
        AppUser googleUser = new AppUser();
        googleUser.setEmail("google-user@example.com");
        googleUser.setDisplayName("Google User");
        googleUser.setPasswordHash(null);
        return users.save(googleUser);
    }

    // --- EXPORT ---

    @Test
    void export_returnsAllOwnedData() throws Exception {
        seedRecord(user);
        seedRecommendation(user);
        seedChat(user);

        mockMvc.perform(get("/api/account/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.email").value("dana@example.com"))
                .andExpect(jsonPath("$.profile.displayName").value("Dana"))
            .andExpect(jsonPath("$.profile.heightCm").value(170.0))
                .andExpect(jsonPath("$.wellnessRecords.length()").value(1))
                .andExpect(jsonPath("$.recommendations.length()").value(1))
                .andExpect(jsonPath("$.chatMessages.length()").value(1))
                .andExpect(jsonPath("$.chatMessages[0].question").value("How do I sleep better?"))
                .andExpect(jsonPath("$.exportedAt").exists());
    }

    @Test
    void export_excludesOtherUsersData() throws Exception {
        AppUser other = seedOtherUser();
        seedRecord(other);
        seedChat(other);

        mockMvc.perform(get("/api/account/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wellnessRecords.length()").value(0))
                .andExpect(jsonPath("$.chatMessages.length()").value(0));
    }

    @Test
    void export_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/account/export"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void export_googleOnlyAccount_returnsOwnedData() throws Exception {
        AppUser googleUser = seedGoogleOnlyUser();
        seedRecord(googleUser);
        String googleToken = jwtService.generateToken(googleUser);

        mockMvc.perform(get("/api/account/export").header("Authorization", "Bearer " + googleToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.email").value("google-user@example.com"))
                .andExpect(jsonPath("$.wellnessRecords.length()").value(1));
    }

    @Test
    void profile_returnsCurrentHeight() throws Exception {
        mockMvc.perform(get("/api/account/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("dana@example.com"))
                .andExpect(jsonPath("$.heightCm").value(170.0));
    }

    @Test
    void profile_updatePersistsHeight() throws Exception {
        var body = new AccountDtos.ProfileUpdateRequest(new BigDecimal("172.5"));

        mockMvc.perform(put("/api/account/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.heightCm").value(172.5));

        assertThat(users.findById(user.getId()).orElseThrow().getHeightCm()).isEqualByComparingTo(new BigDecimal("172.5"));
    }

    // --- DEACTIVATE + REACTIVATE ---

    @Test
    void deactivate_disablesAccountKeepsDataAndRevokesToken() throws Exception {
        seedRecord(user);

        mockMvc.perform(post("/api/account/deactivate").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(users.findById(user.getId()).orElseThrow().isEnabled()).isFalse();
        // Data is retained through a deactivate.
        assertThat(wellnessRecords.findByUserOrderByRecordDateDesc(user)).hasSize(1);

        // The existing token no longer authenticates once the account is disabled.
        mockMvc.perform(get("/api/wellness-records").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_whenDeactivated_returnsForbidden() throws Exception {
        user.setEnabled(false);
        users.save(user);

        var login = new AuthDtos.LoginRequest("dana@example.com", PASSWORD);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isForbidden());
    }

    @Test
    void reactivate_reEnablesAccountAndReturnsToken() throws Exception {
        seedRecord(user);
        user.setEnabled(false);
        users.save(user);

        var reactivate = new AuthDtos.LoginRequest("dana@example.com", PASSWORD);
        mockMvc.perform(post("/api/auth/reactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reactivate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.user.email").value("dana@example.com"));

        assertThat(users.findById(user.getId()).orElseThrow().isEnabled()).isTrue();
        // Data survived the deactivate/reactivate round-trip.
        assertThat(wellnessRecords.findByUserOrderByRecordDateDesc(user)).hasSize(1);
    }

    @Test
    void reactivate_withWrongPassword_returnsUnauthorized() throws Exception {
        user.setEnabled(false);
        users.save(user);

        var reactivate = new AuthDtos.LoginRequest("dana@example.com", "WrongPass1");
        mockMvc.perform(post("/api/auth/reactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reactivate)))
                .andExpect(status().isUnauthorized());

        assertThat(users.findById(user.getId()).orElseThrow().isEnabled()).isFalse();
    }

    // --- DELETE (permanent) ---

    @Test
    void delete_withCorrectPassword_erasesUserAndAllData() throws Exception {
        seedRecord(user);
        seedRecommendation(user);
        seedChat(user);

        var body = new AccountDtos.DeleteAccountRequest(PASSWORD);
        mockMvc.perform(delete("/api/account")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());

        assertThat(users.findById(user.getId())).isEmpty();
        assertThat(wellnessRecords.count()).isZero();
        assertThat(recommendations.count()).isZero();
        assertThat(chatMessages.count()).isZero();
    }

    @Test
    void delete_withWrongPassword_returnsBadRequestAndKeepsData() throws Exception {
        seedRecord(user);

        var body = new AccountDtos.DeleteAccountRequest("WrongPass1");
        mockMvc.perform(delete("/api/account")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                // 400 (not 401/403) so the mobile client does not treat it as session expiry.
                .andExpect(status().isBadRequest());

        assertThat(users.findById(user.getId())).isPresent();
        assertThat(wellnessRecords.findByUserOrderByRecordDateDesc(user)).hasSize(1);
    }

    @Test
    void delete_withoutPasswordForLocalAccount_returnsBadRequestAndKeepsData() throws Exception {
        seedRecord(user);

        var body = new AccountDtos.DeleteAccountRequest(null);
        mockMvc.perform(delete("/api/account")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        assertThat(users.findById(user.getId())).isPresent();
        assertThat(wellnessRecords.findByUserOrderByRecordDateDesc(user)).hasSize(1);
    }

    @Test
    void delete_googleOnlyAccount_erasesUserAndAllOwnedDataWithoutPassword() throws Exception {
        AppUser googleUser = seedGoogleOnlyUser();
        seedRecord(googleUser);
        seedRecommendation(googleUser);
        seedChat(googleUser);
        String googleToken = jwtService.generateToken(googleUser);

        var body = new AccountDtos.DeleteAccountRequest(null);
        mockMvc.perform(delete("/api/account")
                        .header("Authorization", "Bearer " + googleToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());

        assertThat(users.findById(googleUser.getId())).isEmpty();
        assertThat(wellnessRecords.findByUserOrderByRecordDateDesc(googleUser)).isEmpty();
        assertThat(recommendations.findByUserOrderByCreatedAtDesc(googleUser)).isEmpty();
        assertThat(chatMessages.findByUserOrderByCreatedAtDesc(googleUser)).isEmpty();
    }

    @Test
    void delete_onlyRemovesCallersData() throws Exception {
        AppUser other = seedOtherUser();
        seedRecord(other);
        seedRecord(user);

        var body = new AccountDtos.DeleteAccountRequest(PASSWORD);
        mockMvc.perform(delete("/api/account")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());

        // The other user and their data are untouched.
        assertThat(users.findById(other.getId())).isPresent();
        assertThat(wellnessRecords.findByUserOrderByRecordDateDesc(other)).hasSize(1);
    }

    @Test
    void delete_withoutToken_returnsUnauthorized() throws Exception {
        var body = new AccountDtos.DeleteAccountRequest(PASSWORD);
        mockMvc.perform(delete("/api/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }
}
