package sg.edu.nus.iss.wellness.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Exercises the API data models (construction, equality, copy, and destructuring) so the
 * DTO layer is covered without hitting the backend.
 *
 * @author Tiong Zhong Cheng
 */
class ApiModelsTest {

    @Test
    fun `auth request and response models hold their fields`() {
        val register = RegisterRequest("Ann", "ann@example.com", "pw")
        assertEquals("Ann", register.displayName)
        assertEquals("ann@example.com", register.email)

        val login = LoginRequest("ann@example.com", "pw")
        assertEquals("ann@example.com", login.email)

        val google = GoogleAuthRequest("id-token")
        assertEquals(false, google.reactivate)
        assertEquals(true, google.copy(reactivate = true).reactivate)

        val user = UserResponse(1, "Ann", "ann@example.com")
        val response = LoginResponse("jwt", "Bearer", 3600, user)
        assertEquals(user, response.user)
        assertEquals(3600, response.expiresInSeconds)
    }

    @Test
    fun `wellness record request and response destructure and compare`() {
        val request = WellnessRecordRequest(
            recordDate = "2026-01-01",
            sleepHours = 7.5,
            weightKg = 65.0,
            exerciseType = "Running",
            exerciseMinutes = 30,
            moodScore = 4,
            notes = "Good day"
        )
        assertEquals("Running", request.exerciseType)

        val response = WellnessRecordResponse(
            id = 10, recordDate = "2026-01-01", sleepHours = 7.5, weightKg = 65.0,
            exerciseType = "Running", exerciseMinutes = 30, moodScore = 4,
            notes = "Good day", createdAt = null, updatedAt = null
        )
        assertEquals(10L, response.id)
        assertNull(response.createdAt)
        assertEquals(response, response.copy())
        assertNotEquals(response, response.copy(id = 11))
    }

    @Test
    fun `profile and account models`() {
        val profile = AccountProfileResponse(1, "Ann", "ann@example.com", 170.0, "2026-01-01")
        assertEquals(170.0, profile.heightCm!!, 0.001)
        assertEquals(profile, profile.copy())

        assertNull(AccountProfileUpdateRequest(null).heightCm)
        assertEquals("pw", DeleteAccountRequest("pw").password)
    }

    @Test
    fun `chat and recommendation models`() {
        val chat = ChatResponse(
            id = 1, question = "How to sleep?", answer = "Rest",
            sources = listOf(SourceSnippet("Sleep", "Rest well")),
            modelName = "llama", createdAt = null
        )
        assertEquals("How to sleep?", chat.question)
        assertEquals(1, chat.sources!!.size)
        assertEquals(ChatRequest("hi"), ChatRequest("hi"))

        val recommendation = RecommendationResponse(
            id = 5, title = "Sleep more", trendSummary = "Low sleep",
            recommendationText = "Aim for 8h", actionItems = listOf("Sleep earlier"),
            generatedBy = "ai", createdAt = "2026-01-01"
        )
        assertEquals(listOf("Sleep earlier"), recommendation.actionItems)
        assertEquals(recommendation.hashCode(), recommendation.copy().hashCode())
    }
}
