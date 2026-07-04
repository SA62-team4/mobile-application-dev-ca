package sg.edu.nus.iss.wellness;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:application-test.properties")
public class WellnessRecordControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private String userAEmail;
    private String userBEmail;

    private final String password = "password123";

    @BeforeEach
    void setUp() {
        String uniqueId = UUID.randomUUID().toString();

        userAEmail = "alice-" + uniqueId + "@example.test";
        userBEmail = "bob-" + uniqueId + "@example.test";

        registerUser("Alice", userAEmail);
        registerUser("Bob", userBEmail);
    }

    private void registerUser(String displayName, String email) {
        Map<String, Object> request = Map.of(
                "displayName", displayName,
                "email", email,
                "password", password
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/register",
                request,
                Map.class
        );

        assertTrue(
                response.getStatusCode() == HttpStatus.CREATED ||
                response.getStatusCode() == HttpStatus.OK
        );
    }

    private String loginAndGetToken(String email) {
        Map<String, String> request = new HashMap<>();
        request.put("email", email);
        request.put("password", password);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/login",
                request,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        Object token = response.getBody().get("token");
        assertNotNull(token);

        return token.toString();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private Long createRecord(String token, Map<String, Object> payload) {
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, authHeaders(token));

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/wellness-records",
                HttpMethod.POST,
                request,
                Map.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());

        Object id = response.getBody().get("id");
        assertNotNull(id);

        return ((Number) id).longValue();
    }

    private Map<String, Object> validRecordPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("recordDate", "2026-06-29");
        payload.put("sleepHours", 7.5);
        payload.put("exerciseType", "Walking");
        payload.put("exerciseMinutes", 20);
        payload.put("moodScore", 4);
        payload.put("notes", "Felt good");
        return payload;
    }

    @Test
    void ownerCanCrudRecordAndNonOwnerCannotAccessIt() {
        String tokenA = loginAndGetToken(userAEmail);
        String tokenB = loginAndGetToken(userBEmail);

        Map<String, Object> payload = validRecordPayload();

        Long recordId = createRecord(tokenA, payload);

        HttpEntity<Void> requestA = new HttpEntity<>(authHeaders(tokenA));
        HttpEntity<Void> requestB = new HttpEntity<>(authHeaders(tokenB));

        ResponseEntity<Map[]> listResponse = restTemplate.exchange(
                "/api/wellness-records",
                HttpMethod.GET,
                requestA,
                Map[].class
        );

        assertEquals(HttpStatus.OK, listResponse.getStatusCode());
        assertNotNull(listResponse.getBody());

        boolean found = false;

        for (Map record : listResponse.getBody()) {
            if (((Number) record.get("id")).longValue() == recordId) {
                found = true;
                break;
            }
        }

        assertTrue(found);

        ResponseEntity<Map> getResponse = restTemplate.exchange(
                "/api/wellness-records/" + recordId,
                HttpMethod.GET,
                requestA,
                Map.class
        );

        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertNotNull(getResponse.getBody());
        assertEquals(recordId.longValue(), ((Number) getResponse.getBody().get("id")).longValue());

        ResponseEntity<Map> getByNonOwnerResponse = restTemplate.exchange(
                "/api/wellness-records/" + recordId,
                HttpMethod.GET,
                requestB,
                Map.class
        );

        assertEquals(HttpStatus.NOT_FOUND, getByNonOwnerResponse.getStatusCode());

        Map<String, Object> updatePayload = new HashMap<>(payload);
        updatePayload.put("moodScore", 5);

        HttpEntity<Map<String, Object>> updateRequestA = new HttpEntity<>(updatePayload, authHeaders(tokenA));

        ResponseEntity<Map> updateResponse = restTemplate.exchange(
                "/api/wellness-records/" + recordId,
                HttpMethod.PUT,
                updateRequestA,
                Map.class
        );

        assertEquals(HttpStatus.OK, updateResponse.getStatusCode());
        assertNotNull(updateResponse.getBody());
        assertEquals(5, ((Number) updateResponse.getBody().get("moodScore")).intValue());

        HttpEntity<Map<String, Object>> updateRequestB = new HttpEntity<>(updatePayload, authHeaders(tokenB));

        ResponseEntity<Map> updateByNonOwnerResponse = restTemplate.exchange(
                "/api/wellness-records/" + recordId,
                HttpMethod.PUT,
                updateRequestB,
                Map.class
        );

        assertEquals(HttpStatus.NOT_FOUND, updateByNonOwnerResponse.getStatusCode());

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/api/wellness-records/" + recordId,
                HttpMethod.DELETE,
                requestA,
                Void.class
        );

        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());

        ResponseEntity<Map> getAfterDeleteResponse = restTemplate.exchange(
                "/api/wellness-records/" + recordId,
                HttpMethod.GET,
                requestA,
                Map.class
        );

        assertEquals(HttpStatus.NOT_FOUND, getAfterDeleteResponse.getStatusCode());
    }

    @Test
    void unauthenticatedAndInvalidRequestsAreRejected() {
        Map<String, Object> payload = validRecordPayload();

        HttpHeaders unauthHeaders = new HttpHeaders();
        unauthHeaders.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> unauthenticatedRequest = new HttpEntity<>(payload, unauthHeaders);

        ResponseEntity<Map> unauthenticatedResponse = restTemplate.exchange(
                "/api/wellness-records",
                HttpMethod.POST,
                unauthenticatedRequest,
                Map.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, unauthenticatedResponse.getStatusCode());

        String token = loginAndGetToken(userAEmail);

        Map<String, Object> badPayload = new HashMap<>();
        badPayload.put("recordDate", "2026-06-29");
        badPayload.put("sleepHours", 7.0);
        badPayload.put("exerciseType", "Jog");
        badPayload.put("exerciseMinutes", 15);

        HttpEntity<Map<String, Object>> badRequest = new HttpEntity<>(badPayload, authHeaders(token));

        ResponseEntity<Map> badResponse = restTemplate.exchange(
                "/api/wellness-records",
                HttpMethod.POST,
                badRequest,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, badResponse.getStatusCode());
    }
}
