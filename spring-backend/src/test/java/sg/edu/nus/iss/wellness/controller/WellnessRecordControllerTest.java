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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import sg.edu.nus.iss.wellness.dto.WellnessDtos;
import sg.edu.nus.iss.wellness.model.AppUser;
import sg.edu.nus.iss.wellness.model.WellnessRecord;
import sg.edu.nus.iss.wellness.repository.AppUserRepository;
import sg.edu.nus.iss.wellness.repository.WellnessRecordRepository;
import sg.edu.nus.iss.wellness.security.JwtService;

/**
 * Sets up tests for CRUD functionalities
 *
 * @author Tang Chee Seng (with assistance from GenAI - Claude)
 */

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

    // Sets up the account creation for first test user - Bob
    @BeforeEach
    void setUpTester1() {
        testUser = new AppUser();
        testUser.setEmail("boblebuilder@example.com");
        testUser.setPasswordHash(passwordEncoder.encode("LeBuilder123"));
        testUser.setDisplayName("Bob");
        testUser = users.save(testUser);
        token = jwtService.generateToken(testUser);
    }

    // Sets up the account creation for second test user - Gru. Applied only when needed.
    private void setUpTester2() {
        testUser2 = new AppUser();
        testUser2.setEmail("gru@minion.com");
        testUser2.setPasswordHash(passwordEncoder.encode("banana456"));
        testUser2.setDisplayName("Gru");
        testUser2 = users.save(testUser2);
    }

    // Helper to seed a dummy wellness entry into the database
    private WellnessRecord seedRecord(AppUser owner, LocalDate date, int moodScore) {
        WellnessRecord record = new WellnessRecord();
        record.setUser(owner);
        record.setRecordDate(date);
        record.setSleepHours(new BigDecimal("7.0"));
        record.setExerciseType("Walking");
        record.setExerciseMinutes(30);
        record.setMoodScore(moodScore);
        record.setNotes("Feels good building");
        return records.save(record);
    }

// --- CREATE Tests ---
    // Test 1 - Log entry for WellnessRecordRequest created with no errors. Expected 201 Created.
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

    // Test 5 - No. of sleep hours cannot be less than 0 hours. Expected 400 Bad Request.
    @Test
    void logEntryWithSleepHoursBelowZero_returnsError() throws Exception {
        var newEntryDetails = new WellnessDtos.WellnessRecordRequest (
            LocalDate.of(2026, 6, 30), //RecordDate
            new BigDecimal("-1"), //SleepHours
            "Walking", //ExerciseType
            30, //ExerciseMinutes
            4, //Moodscore
            "Couldn't sleep at all!"); //Notes

        mockMvc.perform(post("/api/wellness-records")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(newEntryDetails)))
                .andExpect(status().isBadRequest());

        var savedEntry = records.findByUserOrderByRecordDateDesc(testUser);
        assertThat(savedEntry).isEmpty();
    }

    // Test 6 - No. of exercise minutes cannot be less than 0 minutes. Expected 400 Bad Request.
    @Test
    void logEntryWithExerciseMinsBelowZero_returnsError() throws Exception {
        var newEntryDetails = new WellnessDtos.WellnessRecordRequest (
            LocalDate.of(2026, 6, 30), //RecordDate
            new BigDecimal("8"), //SleepHours
            "NIL", //ExerciseType
            -1, //ExerciseMinutes
            4, //Moodscore
            "No exercise today!"); //Notes

        mockMvc.perform(post("/api/wellness-records")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(newEntryDetails)))
                .andExpect(status().isBadRequest());

        var savedEntry = records.findByUserOrderByRecordDateDesc(testUser);
        assertThat(savedEntry).isEmpty();
    }

    // Test 7 - Required Record Date cannot be null. Expected 400 Bad Request.
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

    // Test 8 - Required Sleep Hours cannot be null. Expected 400 Bad Request.
    @Test
    void logEntryWithoutSleepHours_returnsError() throws Exception {
        var newEntryDetails = new WellnessDtos.WellnessRecordRequest (
            LocalDate.of(2026, 6, 30), //RecordDate
            null, //SleepHours
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

    // Test 9 - Required Mood Score cannot be null. Expected 400 Bad Request.
    @Test
    void logEntryWithoutMoodScore_returnsError() throws Exception {
        var newEntryDetails = new WellnessDtos.WellnessRecordRequest (
            LocalDate.of(2026, 6, 30), //RecordDate
            new BigDecimal("7.5"), //SleepHours
            "Walking", //ExerciseType
            30, //ExerciseMinutes
            null, //Moodscore
            "Rested very well after building!"); //Notes

        mockMvc.perform(post("/api/wellness-records")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(newEntryDetails)))
                .andExpect(status().isBadRequest());

        var savedEntry = records.findByUserOrderByRecordDateDesc(testUser);
        assertThat(savedEntry).isEmpty();
    }

    // Test 10 - Required Exercise Minutes cannot be null. Expected 400 Bad Request.
    @Test
    void logEntryWithoutExerciseMinutes_returnsError() throws Exception {
        var newEntryDetails = new WellnessDtos.WellnessRecordRequest (
            LocalDate.of(2026, 6, 30), //RecordDate
            new BigDecimal("7.5"), //SleepHours
            "Walking", //ExerciseType
            null, //ExerciseMinutes
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

    // Test 11 - Lower boundary values (mood 1, sleep 0, exercise minutes 0) are all valid. Expected 201 Created.
    @Test
    void logEntryWithLowerBoundaryValues_succeeds() throws Exception {
        var newEntryDetails = new WellnessDtos.WellnessRecordRequest (
            LocalDate.of(2026, 6, 30), //RecordDate
            new BigDecimal("0.0"), //SleepHours (lower bound)
            "Walking", //ExerciseType
            0, //ExerciseMinutes (lower bound)
            1, //Moodscore (lower bound)
            "Boundary day"); //Notes

        mockMvc.perform(post("/api/wellness-records")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(newEntryDetails)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.moodScore").value(1))
                .andExpect(jsonPath("$.exerciseMinutes").value(0));

        var savedEntry = records.findByUserOrderByRecordDateDesc(testUser);
        assertThat(savedEntry).hasSize(1);
    }

    // Test 12 - Upper boundary values (mood 5, sleep 24) are all valid. Expected 201 Created.
    @Test
    void logEntryWithUpperBoundaryValues_succeeds() throws Exception {
        var newEntryDetails = new WellnessDtos.WellnessRecordRequest (
            LocalDate.of(2026, 6, 30), //RecordDate
            new BigDecimal("24.0"), //SleepHours (upper bound)
            "Walking", //ExerciseType
            120, //ExerciseMinutes
            5, //Moodscore (upper bound)
            "Boundary day"); //Notes

        mockMvc.perform(post("/api/wellness-records")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(newEntryDetails)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.moodScore").value(5));

        var savedEntry = records.findByUserOrderByRecordDateDesc(testUser);
        assertThat(savedEntry).hasSize(1);
    }

    // Test 13 - Optional fields (exercise type, notes) may be omitted. Expected 201 Created.
    @Test
    void logEntryWithoutOptionalFields_succeeds() throws Exception {
        var newEntryDetails = new WellnessDtos.WellnessRecordRequest (
            LocalDate.of(2026, 6, 30), //RecordDate
            new BigDecimal("7.5"), //SleepHours
            null, //ExerciseType (optional)
            30, //ExerciseMinutes
            4, //Moodscore
            null); //Notes (optional)

        mockMvc.perform(post("/api/wellness-records")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(newEntryDetails)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists());

        var savedEntry = records.findByUserOrderByRecordDateDesc(testUser);
        assertThat(savedEntry).hasSize(1);
    }

    // Test 14 - Valid log entry, but no authorisation header/token. Expected 401 Unauthorised error.
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

    // Test 15 - Valid log entry, but malformed authorisation header/token. Expected 401 Unauthorised error.
    @Test
    void create_withMalformedToken_returnsUnauthorized() throws Exception {
        var newEntryDetails = new WellnessDtos.WellnessRecordRequest(
            LocalDate.of(2026, 6, 30),
            new BigDecimal("7.5"),
            "Walking",
            30,
            5,
            "note");

        mockMvc.perform(post("/api/wellness-records")
                .header("Authorization", "Bearer not-a-real-jwt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newEntryDetails)))
                .andExpect(status().isUnauthorized());

        assertThat(records.findByUserOrderByRecordDateDesc(testUser)).isEmpty();
    }

    // Test 16 - Ownership follows the token: the record is created under testUser (the token owner), not testUser2.
    @Test
    void successfulLogEntryCreationWithWrongUserToken() throws Exception {
        setUpTester2();
        // Sets up testUser2 profile, but the test loads testUser's authorisation token instead.

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

// --- READ Tests ---
    // Test 1 - User can read their own record. Expected 200 OK.
    @Test
    void getById_returnsOwnRecord() throws Exception {
        WellnessRecord seededBob = seedRecord(testUser, LocalDate.of(2026, 6, 30), 4);

        mockMvc.perform(get("/api/wellness-records/{id}", seededBob.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(seededBob.getId()))
                .andExpect(jsonPath("$.moodScore").value(4));
    }

    // Test 2 - User reads only their own records (another user exists in the DB): two testUser entries, newest date first. Expected 200 OK.
    @Test
    void list_returnsOnlyOwnRecords_newestFirst() throws Exception {
        WellnessRecord seededBobDayOne = seedRecord(testUser, LocalDate.of(2026, 6, 30), 4);
        WellnessRecord seededBobDayTwo = seedRecord(testUser, LocalDate.of(2026, 7, 1), 5);

        setUpTester2();
        seedRecord(testUser2, LocalDate.of(2026, 7, 1), 5);

        mockMvc.perform(get("/api/wellness-records")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(seededBobDayTwo.getId()))
                .andExpect(jsonPath("$[0].recordDate").value("2026-07-01"))
                .andExpect(jsonPath("$[1].id").value(seededBobDayOne.getId()))
                .andExpect(jsonPath("$[1].recordDate").value("2026-06-30"));
    }

    // Test 3 - Retrieve testUser records within a date range (2026-07-01 to 2026-07-03), excluding dates outside it. Expected 200 OK.
    @Test
    void list_withDateRange_filtersByDate() throws Exception {
        seedRecord(testUser, LocalDate.of(2026, 6, 30), 4);
        WellnessRecord seededBobDayTwo = seedRecord(testUser, LocalDate.of(2026, 7, 1), 1);
        WellnessRecord seededBobDayThree = seedRecord(testUser, LocalDate.of(2026, 7, 2), 2);
        WellnessRecord seededBobDayFour = seedRecord(testUser, LocalDate.of(2026, 7, 3), 3);
        seedRecord(testUser, LocalDate.of(2026, 7, 4), 5);

        setUpTester2();
        seedRecord(testUser2, LocalDate.of(2026, 7, 1), 5);

        mockMvc.perform(get("/api/wellness-records")
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2026-07-01")
                        .param("to", "2026-07-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(seededBobDayFour.getId()))
                .andExpect(jsonPath("$[0].recordDate").value("2026-07-03"))
                .andExpect(jsonPath("$[0].moodScore").value(3))
                .andExpect(jsonPath("$[1].id").value(seededBobDayThree.getId()))
                .andExpect(jsonPath("$[1].recordDate").value("2026-07-02"))
                .andExpect(jsonPath("$[1].moodScore").value(2))
                .andExpect(jsonPath("$[2].id").value(seededBobDayTwo.getId()))
                .andExpect(jsonPath("$[2].recordDate").value("2026-07-01"))
                .andExpect(jsonPath("$[2].moodScore").value(1));
    }

    // Test 4 - A user with no records gets an empty array, not an error. Expected 200 OK.
    @Test
    void list_withNoRecords_returnsEmptyArray() throws Exception {
        mockMvc.perform(get("/api/wellness-records")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // Test 5 - Pulling an id that does not exist returns 404 Not Found.
    @Test
    void getById_missingId_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/wellness-records/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // Test 6 - Pulling another user's record returns 404 Not Found.
    @Test
    void getById_otherUsersRecord_returnsNotFound() throws Exception {
        seedRecord(testUser, LocalDate.of(2026, 7, 1), 5);

        setUpTester2();
        WellnessRecord seededGru = seedRecord(testUser2, LocalDate.of(2026, 7, 1), 5);

        mockMvc.perform(get("/api/wellness-records/{id}", seededGru.getId())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

// --- UPDATE Tests ---
    // Test 1 - testUser makes a valid edit to an old entry, expected 200 OK, and the change is persisted.
    @Test
    void update_withValidChanges() throws Exception {
        WellnessRecord seededBob = seedRecord(testUser, LocalDate.of(2026, 6, 30), 3);

        var changes = new WellnessDtos.WellnessRecordRequest(
                LocalDate.of(2026, 6, 30), new BigDecimal("8.0"), "Running", 45, 5, "Good day!");

        mockMvc.perform(put("/api/wellness-records/{id}", seededBob.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changes)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordDate").value("2026-06-30"))
                .andExpect(jsonPath("$.moodScore").value(5))
                .andExpect(jsonPath("$.exerciseType").value("Running"));

        WellnessRecord updated = records.findById(seededBob.getId()).orElseThrow();
        assertThat(updated.getMoodScore()).isEqualTo(5);
        assertThat(updated.getExerciseType()).isEqualTo("Running");
        assertThat(updated.getExerciseMinutes()).isEqualTo(45);
    }

    // Test 2 - testUser makes an invalid edit, expected 400 Bad Request, the change is rejected and old data still persists.
    @Test
    void update_withInvalidMoodScore_returnsError() throws Exception {
        WellnessRecord seededBob = seedRecord(testUser, LocalDate.of(2026, 6, 30), 3);

        var moodScoreChange = new WellnessDtos.WellnessRecordRequest(
                LocalDate.of(2026, 6, 30), new BigDecimal("8.0"), "Running", 45, 10, "Best day ever!");

        mockMvc.perform(put("/api/wellness-records/{id}", seededBob.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(moodScoreChange)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("moodScore must be between 1 and 5"));

        WellnessRecord newSeededBob = records.findById(seededBob.getId()).orElseThrow();
        assertThat(newSeededBob.getMoodScore()).isEqualTo(3);
        assertThat(newSeededBob.getExerciseType()).isEqualTo("Walking");
        assertThat(newSeededBob.getExerciseMinutes()).isEqualTo(30);
    }

    // Test 3 - Attempting to update another user's record returns 404 Not Found, and neither record changes.
    @Test
    void update_otherUsersRecord_returnsNotFound() throws Exception {
        WellnessRecord seededBob = seedRecord(testUser, LocalDate.of(2026, 7, 1), 3);

        setUpTester2();
        WellnessRecord seededGru = seedRecord(testUser2, LocalDate.of(2026, 7, 1), 5);

        var moodScoreChange = new WellnessDtos.WellnessRecordRequest(
                LocalDate.of(2026, 6, 30), new BigDecimal("8.0"), "Running", 45, 3, "Best day ever!");

        mockMvc.perform(put("/api/wellness-records/{id}", seededGru.getId())
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(moodScoreChange)))
                    .andExpect(status().isNotFound());

        WellnessRecord newSeededBob = records.findById(seededBob.getId()).orElseThrow();
        assertThat(newSeededBob.getMoodScore()).isEqualTo(3);
        assertThat(newSeededBob.getExerciseType()).isEqualTo("Walking");
        assertThat(newSeededBob.getExerciseMinutes()).isEqualTo(30);

        WellnessRecord newSeededGru = records.findById(seededGru.getId()).orElseThrow();
        assertThat(newSeededGru.getMoodScore()).isEqualTo(5);
        assertThat(newSeededGru.getExerciseType()).isEqualTo("Walking");
        assertThat(newSeededGru.getExerciseMinutes()).isEqualTo(30);
    }

// --- DELETE Tests ---
    // Test 1 - Delete your own record. Expected 204 No Content, and the row is gone.
    @Test
    void delete_ownRecord_returnsNoContentAndRemovesRow() throws Exception {
        WellnessRecord seededBob = seedRecord(testUser, LocalDate.of(2026, 6, 30), 4);

        mockMvc.perform(delete("/api/wellness-records/{id}", seededBob.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(records.findById(seededBob.getId())).isEmpty();
    }

    // Test 2 - Delete a non-existent id. Expected 404 Not Found, and nothing is deleted.
    @Test
    void delete_nonExistentID_returnsNotFound() throws Exception {
        WellnessRecord seededBob = seedRecord(testUser, LocalDate.of(2026, 6, 30), 4);

        mockMvc.perform(delete("/api/wellness-records/999999")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        WellnessRecord stillThere = records.findById(seededBob.getId()).orElseThrow();
        assertThat(stillThere.getMoodScore()).isEqualTo(4);
        assertThat(stillThere.getExerciseType()).isEqualTo("Walking");
        assertThat(stillThere.getExerciseMinutes()).isEqualTo(30);
    }

    // Test 3 - Delete another user's entry. Expected 404 Not Found, and nothing is deleted.
    @Test
    void delete_otherUsersRecord_returnsNotFound() throws Exception {
        WellnessRecord seededBob = seedRecord(testUser, LocalDate.of(2026, 6, 30), 4);

        setUpTester2();
        WellnessRecord seededGru = seedRecord(testUser2, LocalDate.of(2026, 7, 1), 5);

        mockMvc.perform(delete("/api/wellness-records/{id}", seededGru.getId())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        WellnessRecord stillBob = records.findById(seededBob.getId()).orElseThrow();
        assertThat(stillBob.getMoodScore()).isEqualTo(4);
        assertThat(stillBob.getExerciseType()).isEqualTo("Walking");
        assertThat(stillBob.getExerciseMinutes()).isEqualTo(30);

        WellnessRecord stillGru = records.findById(seededGru.getId()).orElseThrow();
        assertThat(stillGru.getMoodScore()).isEqualTo(5);
        assertThat(stillGru.getExerciseType()).isEqualTo("Walking");
        assertThat(stillGru.getExerciseMinutes()).isEqualTo(30);
    }
}