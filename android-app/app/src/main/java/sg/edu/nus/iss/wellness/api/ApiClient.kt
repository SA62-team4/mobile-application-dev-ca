package sg.edu.nus.iss.wellness.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import sg.edu.nus.iss.wellness.BuildConfig
import sg.edu.nus.iss.wellness.TokenStore
import java.util.concurrent.TimeUnit

/**
 * Creates Retrofit services with JWT authentication.
 *
 * A single response interceptor centralises session-expiry handling: any 401
 * (expired / malformed / missing token) or 403 clears the stored token and
 * signals the app to return to the login screen, so every API call is covered
 * uniformly rather than each screen re-implementing the check.
 *
 * @author SA62 Team, JustinChua97
 */
object ApiClient {
    /**
     * @param tokenStore stored credentials; cleared automatically on 401/403.
     * @param onSessionExpired invoked (on an OkHttp background thread) when a call
     *   returns 401/403, after the token is cleared. Callers should marshal any
     *   navigation onto the main thread. May be null for unauthenticated screens
     *   (login/register) that handle auth failures inline.
     */
    fun create(tokenStore: TokenStore, onSessionExpired: (() -> Unit)? = null): ApiService {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(150, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            // This is a JSON API client: never silently follow a 3xx redirect. Auth
            // failures must surface as 401/403 status codes the app can act on, not be
            // swallowed by the HTTP stack chasing a Location header.
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor { chain ->
                val token = tokenStore.token()
                val request = if (token.isNullOrBlank()) {
                    chain.request()
                } else {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                }
                chain.proceed(request)
            }
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (response.code == 401 || response.code == 403) {
                    tokenStore.clear()
                    onSessionExpired?.invoke()
                }
                response
            }
            .addInterceptor(logger)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
