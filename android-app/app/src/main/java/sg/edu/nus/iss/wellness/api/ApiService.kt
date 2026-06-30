package sg.edu.nus.iss.wellness.api

import retrofit2.http.*

/**
 * Retrofit contract for the Spring Boot backend.
 *
 * @author SA62 Team
 */
interface ApiService {
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): UserResponse

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("api/auth/google")
    suspend fun googleLogin(@Body request: GoogleAuthRequest): LoginResponse

    @POST("api/auth/logout")
    suspend fun logout()

    @GET("api/wellness-records")
    suspend fun records(): List<WellnessRecordResponse>

    @POST("api/wellness-records")
    suspend fun createRecord(@Body request: WellnessRecordRequest): WellnessRecordResponse

    @PUT("api/wellness-records/{id}")
    suspend fun updateRecord(@Path("id") id: Long, @Body request: WellnessRecordRequest): WellnessRecordResponse

    @DELETE("api/wellness-records/{id}")
    suspend fun deleteRecord(@Path("id") id: Long)

    @GET("api/chat/messages")
    suspend fun chatHistory(): List<ChatResponse>

    @POST("api/chat/messages")
    suspend fun sendChat(@Body request: ChatRequest): ChatResponse

    @GET("api/recommendations")
    suspend fun recommendations(): List<RecommendationResponse>

    @POST("api/recommendations/generate")
    suspend fun generateRecommendation(): RecommendationResponse
}

