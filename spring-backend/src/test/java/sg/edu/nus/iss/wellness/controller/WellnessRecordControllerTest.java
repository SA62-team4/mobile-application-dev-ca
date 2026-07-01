package sg.edu.nus.iss.wellness.controller;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import sg.edu.nus.iss.wellness.dto.WellnessDtos;
import sg.edu.nus.iss.wellness.model.AppUser;
import sg.edu.nus.iss.wellness.repository.AppUserRepository;
import sg.edu.nus.iss.wellness.repository.WellnessRecordRepository;
import sg.edu.nus.iss.wellness.security.JwtService;

// Written by Tang Chee Seng (with assistance from GenAI - Claude)

@SpringBootTest
@AutoConfigureMockMvc
@Transactional

public class WellnessRecordControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private WellnessRecordRepository records;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private AppUser testUser; //Seeded test account whose token will authenticate the requests below

    private AppUser testUser2;

    private String token; 

    // Sets up the account creation for the test user - Bob
    @BeforeEach
    void setUpTester1() {
        testUser = new AppUser();
        testUser.setEmail("boblebuilder@example.com"); 
        testUser.setPasswordHash(passwordEncoder.encode("LeBuilder123"));
        testUser.setDisplayName("Bob");
        testUser = users.save(testUser);
        token = jwtService.generateToken(testUser);
    }

    // Test 1 - Log entry for WellnessRecordRequest created with no errors. Expected 200 OK.
    @Test
    void successfulLogEntryCreationAndPersists() throws Exception {
        var newEntryDetails = new WellnessDtos.WellnessRecordRequest (
            LocalDate.of(2026, 6, 30), //RecordDate
            new BigDecimal("7.5"), //SleepHours
            "Walking", //ExerciseType
            30, //ExerciseMinutes
            5, //Moodscore
            "Felt good building today"); //Notes
        
        mockMvc.perform(post("/api/wellness-records")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(newEntryDetails))
        )

            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.moodScore").value(5))
            .andExpect(jsonPath("$.createdAt").exists());

        var savedEntry = records.findByUserOrderByRecordDateDesc(testUser);
        assertThat(savedEntry).hasSize(1);
        assertThat(savedEntry.get(0).getUser().getId()).isEqualTo(testUser.getId());
    }

    // Test 2 - Mood score cannot have a value above 5. Expected 400 Bad Request.
    @Test
    void logEntryWithMoodScoreAbove5_returnsError() throws Exception {
        var newEntryDetails = new WellnessDtos.WellnessRecordRequest (
            LocalDate.of(2026, 6, 30), //RecordDate
            new BigDecimal("7.5"), //SleepHours
            "Walking", //ExerciseType
            30, //ExerciseMinutes
            6, //Moodscore
            "Felt good building today"); //Notes
        
        mockMvc.perform(post("/api/wellness-records")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(newEntryDetails)))
                .andExpect(status().isBadRequest());
           

        var savedEntry = records.findByUserOrderByRecordDateDesc(testUser);
        assertThat(savedEntry).isEmpty();
    }

    // Test 3 - Mood score cannot have a value below 1. Expected 400 Bad Request.
    @Test
    void logEntryWithMoodScoreBelow1_returnsError() throws Exception {
        var newEntryDetails = new WellnessDtos.WellnessRecordRequest (
            LocalDate.of(2026, 6, 30), //RecordDate
            new BigDecimal("7.5"), //SleepHours
            "Walking", //ExerciseType
            30, //ExerciseMinutes
            0, //Moodscore
            "Felt sad not building today"); //Notes
        
        mockMvc.perform(post("/api/wellness-records")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(newEntryDetails)))
                .andExpect(status().isBadRequest());
           

        var savedEntry = records.findByUserOrderByRecordDateDesc(testUser);
        assertThat(savedEntry).isEmpty();
    }

    // Test 4 - No. of sleep hours cannot exceed 24 hours. Expected 400 Bad Request.
    @Test
    void logEntryWithSleepHoursAbove24_returnsError() throws Exception {
        var newEntryDetails = new WellnessDtos.WellnessRecordRequest (
            LocalDate.of(2026, 6, 30), //RecordDate
            new BigDecimal("24.01"), //SleepHours
            "Walking", //ExerciseType
            30, //ExerciseMinutes
            4, //Moodscore
            "Rested very well after building!"); //Notes
        
        mockMvc.perform(post("/api/wellness-records")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(newEntryDetails)))
                .andExpect(status().isBadRequest());
           
        var savedEntry = records.findByUserOrderByRecordDateDesc(testUser);
        assertThat(savedEntry).isEmpty();
    }

    // Test 5 - Required Record Date cannot be null. Expected 400 Bad Request.
    @Test
    void logEntryWithoutRecordDate_returnsError() throws Exception {
        var newEntryDetails = new WellnessDtos.WellnessRecordRequest (
            null, //RecordDate
            new BigDecimal("12"), //SleepHours
            "Walking", //ExerciseType
            30, //ExerciseMinutes
            4, //Moodscore
            "Rested very well after building!"); //Notes
        
        mockMvc.perform(post("/api/wellness-records")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(newEntryDetails)))
                .andExpect(status().isBadRequest());
           
        var savedEntry = records.findByUserOrderByRecordDateDesc(testUser);
        assertThat(savedEntry).isEmpty();
    }

    // Test 6 - Valid log entry, but no authorisation header/token. Expected 403 Forbidden error.
    // NOTE: Specs indicate that a 401 error is expected. Pending confirmation if this is correct.
    @Test
    void UnauthorizedEntryValidBodyNoToken() throws Exception {
        var newEntryDetails = new WellnessDtos.WellnessRecordRequest (
            LocalDate.of(2026, 6, 30), //RecordDate
            new BigDecimal("7.5"), //SleepHours
            "Walking", //ExerciseType
            30, //ExerciseMinutes
            5, //Moodscore
            "Felt good building today"); //Notes
        
        mockMvc.perform(post("/api/wellness-records")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(newEntryDetails)))
                .andExpect(status().isUnauthorized());

        var savedEntry = records.findByUserOrderByRecordDateDesc(testUser);
        assertThat(savedEntry).isEmpty();
    }

    // Test 7 - Valid log entry, but wrong authorisation header (testUser instead of testUser2)

    // Sets up the account creation for the test user - Gru
    private void setUpTester2() {
        testUser2 = new AppUser();
        testUser2.setEmail("gru@minion.com"); 
        testUser2.setPasswordHash(passwordEncoder.encode("banana456"));
        testUser2.setDisplayName("Gru");
        testUser2 = users.save(testUser2);
    }

    @Test
    void successfulLogEntryCreationUnderTokenUserNotActualUser() throws Exception {
        setUpTester2();

        var newEntryDetails = new WellnessDtos.WellnessRecordRequest (
            LocalDate.of(2026, 6, 30), //RecordDate
            new BigDecimal("7.5"), //SleepHours
            "Taking over the world", //ExerciseType
            30, //ExerciseMinutes
            5, //Moodscore
            "Felt good being evil today"); //Notes

    mockMvc.perform(post("/api/wellness-records")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(newEntryDetails)))
            .andExpect(status().isCreated());

        var savedEntryBob = records.findByUserOrderByRecordDateDesc(testUser);

        var savedEntryGru = records.findByUserOrderByRecordDateDesc(testUser2);

        assertThat(savedEntryBob).hasSize(1);
        assertThat(savedEntryBob.get(0).getUser().getId()).isEqualTo(testUser.getId());
        assertThat(savedEntryGru).isEmpty();
    }
}
