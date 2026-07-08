package sg.edu.nus.iss.wellness.api

import retrofit2.http.*

/**
 * Retrofit contract for the Spring Boot backend.
 *
 * @author Kumaraguru Surya, Tiong Zhong Cheng, Chua Wei Yi Justin
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

    // --- Privacy / account management (S-03) ---

    /** Full JSON copy of the caller's data. Returned as a raw body so it can be
     *  written verbatim to a user-chosen file. */
    @GET("api/account/export")
    suspend fun exportAccountData(): retrofit2.Response<okhttp3.ResponseBody>

    /** Reversible: disables the account and blocks sign-in, keeps all data. */
    @POST("api/account/deactivate")
    suspend fun deactivateAccount()

    /** Permanent erasure; local accounts include password, Google-only accounts do not. */
    @HTTP(method = "DELETE", path = "api/account", hasBody = true)
    suspend fun deleteAccount(@Body request: DeleteAccountRequest)

    /** Re-enables a deactivated account and logs the user back in. */
    @POST("api/auth/reactivate")
    suspend fun reactivate(@Body request: LoginRequest): LoginResponse
}
