package sg.edu.nus.iss.wellness.api

/**
 * API request and response models.
 *
 * @author Kumaraguru Surya, Tiong Zhong Cheng, Chua Wei Yi Justin
 */
data class RegisterRequest(val displayName: String, val email: String, val password: String)
data class LoginRequest(val email: String, val password: String)
data class GoogleAuthRequest(val idToken: String, val reactivate: Boolean = false)
data class UserResponse(val id: Long, val displayName: String, val email: String)
data class LoginResponse(val token: String, val tokenType: String, val expiresInSeconds: Long, val user: UserResponse)

data class WellnessRecordRequest(
    val recordDate: String,
    val sleepHours: Double,
    val exerciseType: String?,
    val exerciseMinutes: Int,
    val moodScore: Int,
    val notes: String?
)

data class WellnessRecordResponse(
    val id: Long,
    val recordDate: String,
    val sleepHours: Double,
    val exerciseType: String?,
    val exerciseMinutes: Int,
    val moodScore: Int,
    val notes: String?,
    val createdAt: String?,
    val updatedAt: String?
)

data class DeleteAccountRequest(val password: String?)

data class ChatRequest(
    val question: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class SourceSnippet(
    val title: String, 
    val snippet: String
)

data class ChatResponse(
    val id: Long,
    val question: String,
    val answer: String,
    val sources: List<SourceSnippet>?,
    val modelName: String?,
    val createdAt: String?
)

data class RecommendationResponse(
    val id: Long,
    val title: String,
    val trendSummary: String,
    val recommendationText: String,
    val actionItems: List<String>,
    val generatedBy: String?,
    val createdAt: String?
)
