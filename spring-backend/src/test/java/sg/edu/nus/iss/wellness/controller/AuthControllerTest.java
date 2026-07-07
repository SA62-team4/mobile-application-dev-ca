package sg.edu.nus.iss.wellness.controller;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import sg.edu.nus.iss.wellness.dto.AuthDtos;
import sg.edu.nus.iss.wellness.model.AppUser;
import sg.edu.nus.iss.wellness.repository.AppUserRepository;
import sg.edu.nus.iss.wellness.security.JwtService;

/**
 * End-to-end auth-contract tests (E1 Auth &amp; Security): registration hashes the password and
 * rejects duplicate emails; login returns a JWT for valid credentials and 401s for invalid ones;
 * protected endpoints reject a missing or malformed token. Runs against the in-memory H2 database,
 * so it needs no external services in CI.
 *
 * @author Chua Wei Yi Justin
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtService jwtService;

    private static final String EMAIL = "alice@example.com";
    private static final String PASSWORD = "Password123";

    // Seeds an existing account directly in the database (password stored as a BCrypt hash).
    private AppUser seedUser() {
        AppUser user = new AppUser();
        user.setDisplayName("Alice");
        user.setEmail(EMAIL);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return users.save(user);
    }

    // ---- Registration ----

    @Test
    void register_storesHashedPassword_andReturnsCreated() throws Exception {
        var request = new AuthDtos.RegisterRequest("Alice", EMAIL, PASSWORD);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.displayName").value("Alice"))
                // The raw password must never appear in the response.
                .andExpect(jsonPath("$.password").doesNotExist());

        AppUser saved = users.findByEmail(EMAIL).orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(saved.getPasswordHash()).startsWith("$2"); // BCrypt hash
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();
    }

    @Test
    void register_duplicateEmail_returnsConflict() throws Exception {
        seedUser();
        var request = new AuthDtos.RegisterRequest("Alice Again", EMAIL, "AnotherPass123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());

        // The original account is untouched (no second row created).
        assertThat(users.findByEmail(EMAIL).orElseThrow().getDisplayName()).isEqualTo("Alice");
    }

    // ---- Login ----

    @Test
    void login_validCredentials_returnsBearerJwt() throws Exception {
        seedUser();
        var request = new AuthDtos.LoginRequest(EMAIL, PASSWORD);

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        String token = json.get("token").asText();
        assertThat(token).isNotBlank();
        // The returned token is a real, verifiable JWT for this user.
        assertThat(jwtService.extractSubject(token)).isEqualTo(EMAIL);
    }

    @Test
    void login_wrongPassword_returnsUnauthorized() throws Exception {
        seedUser();
        var request = new AuthDtos.LoginRequest(EMAIL, "WrongPassword123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_unknownEmail_returnsUnauthorized() throws Exception {
        var request = new AuthDtos.LoginRequest("nobody@example.com", PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ---- Protected endpoint ----

    @Test
    void protectedEndpoint_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/wellness-records"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withMalformedToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/wellness-records")
                        .header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }
}
