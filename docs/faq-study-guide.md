# FAQ Study Guide

<!-- @author Tiong Zhong Cheng -->

## Purpose

Use this as a quick oral-demo and revision guide. Answers are intentionally short and simple.

## System Overview

### What is this project?
An AI-enabled wellness mobile app with Android, Spring Boot, MySQL, Python AI, Chroma, and Ollama.

### What is the main user flow?
Register or login, create wellness records, ask the chatbot, and generate recommendations.

### What is the Android app responsible for?
User interface, form input, token storage, navigation, and REST calls to Spring Boot.

### What is Spring Boot responsible for?
Authentication, authorization, business rules, MySQL writes, and AI orchestration.

### What is MySQL used for?
Transactional data such as users, wellness records, chat messages, and recommendations.

### What is Python AI responsible for?
RAG retrieval, Ollama calls, and recommendation generation logic.

### What is Chroma used for?
It stores vector embeddings for knowledge-base retrieval.

### What is Ollama used for?
It runs the local LLM and embedding model.

### Why does Android not call MySQL directly?
That would bypass backend security and ownership checks.

### Why does Android not call Python directly?
The backend must control auth, recent records, persistence, and business rules.

### What is the canonical backend?
The Java Spring Boot backend.

### Is the .NET backend required?
No. It is optional cold-standby or bonus evidence.

### What makes the app AI-enabled?
It uses local RAG chat and an agentic recommendation workflow.

### What makes the AI local?
Ollama and the curated knowledge base run locally without paid cloud LLM APIs.

### What is the default generation model?
`qwen2.5:1.5b`.

### What is the default embedding model?
`nomic-embed-text`.

### What is the main architecture rule?
Android calls Spring Boot only; Spring Boot coordinates database and AI work.

### Why is this architecture useful?
It keeps security, persistence, and AI boundaries clear.

## Android Module

### What language is the Android app written in?
Kotlin.

### What UI approach does the Android app use?
XML layouts with AppCompat and View Binding.

### Why not Jetpack Compose?
The project spec requires XML layouts.

### What are the main Android screens?
Login, register, dashboard, record form, chat, recommendations, profile, and privacy.

### What is the authenticated landing screen?
The dashboard.

### What does the dashboard show?
Wellness summaries, records, BMI/body metrics, and recommendation teasers.

### What does `LoginActivity` do?
It handles email/password login and navigation after successful authentication.

### What does `RegisterActivity` do?
It creates a new account through the backend.

### What does `DashboardActivity` do?
It loads wellness records and profile data, then displays trends and records.

### What does `RecordFormActivity` do?
It creates or updates a wellness record.

### What does `ChatActivity` do?
It shows chat history and sends wellness questions to the backend.

### What does `RecommendationsActivity` do?
It lists recommendations and can request a new AI recommendation.

### What does `ProfileActivity` do?
It shows account information and lets the user update height for BMI.

### What does `PrivacyActivity` do?
It supports account export, deactivation, and deletion features.

### How does Android navigate between main screens?
With explicit `Intent`s and a shared bottom navigation layout.

### What is `EdgeToEdge`?
A helper that pads the root view by the system-bar insets so content is not hidden by the status or navigation bar.

Example:

```kotlin
root.setOnApplyWindowInsetsListener { view, insets ->
    val bars = insets.getInsets(WindowInsets.Type.systemBars())
    view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
    insets
}
```

### What is `ExerciseTypeOptions`?
A canonical list of exercise choices plus an alias map so old free-text values still select the right spinner row.

Example:

```kotlin
private val aliases = mapOf("jog" to RUNNING, "bike" to CYCLING, "gym" to STRENGTH)

fun selectedIndexFor(storedValue: String?): Int {
    val normalized = storedValue?.trim().orEmpty().lowercase()
    val option = aliases[normalized] ?: if (normalized.isBlank()) NONE else "Other"
    return options.indexOf(option).takeIf { it >= 0 } ?: 0
}
```

### Why keep an alias map?
Records saved before the spinner existed contain free text, and the form must still open on the correct option.

### What does `TokenStore` actually save?
The token plus display name, email, and the Google photo URL, all in `SharedPreferences`.

Example:

```kotlin
fun save(token: String, displayName: String, email: String, photoUrl: String? = null) {
    prefs.edit {
        putString("token", token)
        putString("displayName", displayName)
        putString("email", email)
        putString("photoUrl", photoUrl)
    }
}
```

### What is `LoginLockout` on Android?
A pure helper that turns the backend's `Retry-After` seconds into a friendly lockout message.

Example:

```kotlin
fun message(retryAfterSeconds: Long?): String {
    if (retryAfterSeconds == null || retryAfterSeconds <= 0) {
        return "Too many failed attempts. Please wait a few minutes and try again."
    }
    val minutes = retryAfterSeconds / 60
    val remainder = retryAfterSeconds % 60
    val window = if (minutes > 0) "${minutes}m ${remainder}s" else "${remainder}s"
    return "Too many failed attempts. Try again in $window."
}
```

### Why is `LoginLockout` a separate object?
It has no Android dependencies, so it can be unit-tested on the plain JVM.

### What is View Binding?
A generated binding class that gives type-safe access to XML views.

Example:

```kotlin
private lateinit var binding: ActivityLoginBinding

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityLoginBinding.inflate(layoutInflater)
    setContentView(binding.root)
    binding.loginButton.setOnClickListener { login() }
}
```

### Why use View Binding?
It avoids many `findViewById` mistakes and makes view references clearer.

### What is `TokenStore`?
A helper that stores and clears the JWT on Android.

Example:

```kotlin
val token = tokenStore.token()
if (!token.isNullOrBlank()) {
    request.newBuilder()
        .addHeader("Authorization", "Bearer $token")
        .build()
}
```

### Why store the JWT?
The app needs it for authenticated backend calls.

### What happens when the token expires?
The app clears the token and sends the user back to login.

### How does Android show API errors?
Screens display friendly messages instead of raw stack traces.

### Why are loading states important?
Network and AI calls can be slow, so the user needs feedback.

## Retrofit And Networking

### What is Retrofit?
A library that turns Kotlin interfaces into HTTP API clients.

Example:

```kotlin
Retrofit.Builder()
    .baseUrl(BuildConfig.API_BASE_URL)
    .addConverterFactory(GsonConverterFactory.create())
    .build()
    .create(ApiService::class.java)
```

### What is `ApiService`?
The Retrofit interface that declares backend endpoints.

Example:

```kotlin
interface ApiService {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("api/wellness-records")
    suspend fun records(): List<WellnessRecordResponse>
}
```

### What is `ApiClient`?
The object that builds Retrofit with OkHttp, Gson, and auth interceptors.

### What does `@GET` mean?
The method makes an HTTP GET request.

Example:

```kotlin
@GET("api/chat/messages")
suspend fun chatHistory(): List<ChatResponse>
```

### What does `@POST` mean?
The method makes an HTTP POST request.

Example:

```kotlin
@POST("api/wellness-records")
suspend fun createRecord(@Body request: WellnessRecordRequest): WellnessRecordResponse
```

### What does `@PUT` mean?
The method updates a resource through HTTP PUT.

Example:

```kotlin
@PUT("api/wellness-records/{id}")
suspend fun updateRecord(
    @Path("id") id: Long,
    @Body request: WellnessRecordRequest
): WellnessRecordResponse
```

### What does `@DELETE` mean?
The method deletes a resource through HTTP DELETE.

Example:

```kotlin
@DELETE("api/wellness-records/{id}")
suspend fun deleteRecord(@Path("id") id: Long)
```

### What does `@Body` do?
It serializes a Kotlin object as the JSON request body.

Example:

```kotlin
data class LoginRequest(
    val email: String,
    val password: String
)
```

### What does `@Path` do?
It inserts a value into the URL path.

Example:

```kotlin
// id = 5 calls /api/wellness-records/5
@GET("api/wellness-records/{id}")
suspend fun record(@Path("id") id: Long): WellnessRecordResponse
```

### What does `suspend` mean in Retrofit methods?
The call runs asynchronously using Kotlin coroutines.

Example:

```kotlin
lifecycleScope.launch {
    val records = api.records()
    showRecords(records)
}
```

### Why use coroutines?
They keep network calls off the main UI thread.

### What is OkHttp?
The lower-level HTTP client used by Retrofit.

### What is an OkHttp interceptor?
Code that can inspect or modify requests and responses.

Example:

```kotlin
.addInterceptor { chain ->
    val response = chain.proceed(chain.request())
    if (response.code == 401 || response.code == 403) tokenStore.clear()
    response
}
```

### How is the JWT attached to requests?
An interceptor adds `Authorization: Bearer <token>`.

Example:

```kotlin
val authenticated = request.newBuilder()
    .addHeader("Authorization", "Bearer $token")
    .build()
```

### Why does `ApiClient` handle 401 and 403 centrally?
So every screen gets consistent session-expiry behavior.

### What is Gson used for?
It converts JSON to Kotlin objects and Kotlin objects to JSON.

### Why are timeouts longer for API calls?
Local AI responses can take longer than simple CRUD calls.

### Why disable redirects in the API client?
REST auth failures should stay as 401 or 403, not be hidden by redirects.

### What is `BuildConfig.API_BASE_URL`?
The backend base URL configured at build time.

### What should Android call for chat?
Spring Boot chat endpoints, not Python AI endpoints.

### Why does streaming not use Retrofit?
Retrofit hands back a parsed body only after the response completes, so a raw OkHttp call is used to read frames as they arrive.

### What is `ChatStreamClient`?
A raw OkHttp client that opens the SSE endpoint, parses each frame, and emits it on the main thread.

Example:

```kotlin
call.execute().use { response ->
    val source = response.body?.source() ?: return
    while (!source.exhausted()) {
        val line = source.readUtf8Line() ?: break
        val event = ChatSseParser.parseLine(line) ?: continue
        withContext(Dispatchers.Main) { onEvent(event) }
        if (event is ChatStreamEvent.Done || event is ChatStreamEvent.Error) break
    }
}
```

### What is a sealed interface used for here?
`ChatStreamEvent` models the four possible frames so `when` handling is exhaustive.

Example:

```kotlin
sealed interface ChatStreamEvent {
    data class Sources(val sources: List<SourceSnippet>) : ChatStreamEvent
    data class Token(val text: String) : ChatStreamEvent
    data class Done(val id: Long, val modelName: String?, ...) : ChatStreamEvent
    data class Error(val message: String) : ChatStreamEvent
}
```

### What is `ChatSseParser`?
A pure object that turns one raw SSE line into a `ChatStreamEvent`, so framing can be unit-tested without a network call.

Example:

```kotlin
fun parseLine(line: String): ChatStreamEvent? {
    if (!line.startsWith("data:")) return null
    val payload = line.substring("data:".length).trim()
    if (payload.isEmpty()) return null
    return parse(payload)
}
```

### Why is `callTimeout` zero on the stream client?
The read timeout bounds the gap between tokens; the whole answer is left untimed so slow CPU generation can finish.

Example:

```kotlin
.readTimeout(180, TimeUnit.SECONDS)   // gap between tokens
.callTimeout(0, TimeUnit.SECONDS)     // no cap on total answer time
```

### How is a cancelled coroutine handled during streaming?
Cancelling the job cancels the OkHttp call so the socket is released.

Example:

```kotlin
val cancellation = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
```

### Can the user stop a streaming answer?
Yes. A stop button appears while streaming and cancels the coroutine that owns the call.

Example:

```kotlin
private var streamJob: Job? = null

binding.stopButton.setOnClickListener { stopChat() }

private fun stopChat() {
    streamJob?.cancel()
}
```

### Why is stopping useful?
Local CPU generation can be slow, so the user should not be stuck waiting for an unwanted answer.

### What caused the error view to flash at the end of a streamed answer?
After the terminal `done` frame, the read loop kept reading and threw when the server closed the stream, which briefly showed the error view.

### How was the flashing fixed?
Two defences: the client stops reading after a terminal frame, and the screen ignores late failures once a terminal frame was handled.

Example:

```kotlin
// ChatStreamClient: stop after terminal frames.
if (event is ChatStreamEvent.Done || event is ChatStreamEvent.Error) break

// ChatActivity: safety net against a late close error.
if (state.terminal) return
```

### Why keep both defences?
The parser fix removes the cause; the terminal flag protects against any other late failure.

## Android Adapters And Lists

### What is an adapter?
A bridge between a data list and a UI list.

Example:

```kotlin
val adapter = RecordsAdapter(this, records, onEdit, onDelete)
recordsListView.adapter = adapter
```

### What list style does this app use?
`ListView` with `ArrayAdapter`.

### What is `RecordsAdapter`?
It renders wellness record rows with edit and delete buttons.

### What is `ChatAdapter`?
It renders question and answer chat rows.

### What is `RecommendationAdapter`?
It renders recommendation cards with action items.

### What is `getView()`?
The adapter method that creates or reuses a row view.

Example:

```kotlin
override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
    val view = convertView ?: LayoutInflater.from(context)
        .inflate(R.layout.row_record, parent, false)
    val record = items[position]
    view.findViewById<TextView>(R.id.recordDate).text = record.recordDate
    return view
}
```

### What is `convertView`?
An old row view that can be reused for better performance.

Example:

```kotlin
val view = convertView ?: inflater.inflate(R.layout.row_record, parent, false)
```

### Why reuse row views?
It avoids inflating a new layout for every visible row repeatedly.

### What is a ViewHolder?
A small object that stores references to row views.

Example:

```kotlin
private class ViewHolder(view: View) {
    val date: TextView = view.findViewById(R.id.recordDate)
    val summary: TextView = view.findViewById(R.id.recordSummary)
}
```

### Why use a ViewHolder?
It avoids repeated `findViewById` calls.

Example:

```kotlin
val holder = if (convertView == null) {
    ViewHolder(view).also { view.tag = it }
} else {
    view.tag as ViewHolder
}
```

### What does `notifyDataSetChanged()` do?
It tells the list to redraw because data changed.

Example:

```kotlin
clear()
addAll(newItems)
notifyDataSetChanged()
```

### Why does `ChatAdapter.submit()` replace rows?
It supports history loading and live streaming updates.

### Why are source chips hidden in chat rows?
The answer is designed to read standalone in the UI.

### How are record row buttons handled?
The adapter receives callbacks for edit and delete actions.

### Why format timestamps in the recommendation adapter?
Backend timestamps need a readable local display format.

## Android Data And UI Helpers

### What are DTO models in Android?
Kotlin data classes matching backend JSON request and response shapes.

Example:

```kotlin
data class WellnessRecordRequest(
    val recordDate: String,
    val sleepHours: Double,
    val exerciseMinutes: Int,
    val moodScore: Int
)
```

### Why keep DTOs separate?
They make API data explicit and easier to test.

### What does `DashboardDataHelper` do?
It calculates summaries, trends, and BMI data for the dashboard.

Example:

```kotlin
val bmi = if (weightKg != null && heightCm != null) {
    weightKg / ((heightCm / 100.0) * (heightCm / 100.0))
} else {
    null
}
```

### Why calculate BMI on Android?
The dashboard can derive it from profile height and record weight.

### What is a sparkline?
A small chart showing a trend.

### What does `SparklineView` do?
It draws compact trend charts and supports tap or drag tooltips.

### What is a custom view?
A class extending `View` that draws itself in `onDraw` and handles its own touch input.

### How does the sparkline tooltip work?
`onTouchEvent` finds the nearest point to the touch and `onDraw` renders a small rounded box with its value.

Example:

```kotlin
override fun onTouchEvent(event: MotionEvent): Boolean {
    // Find the nearest data point to the touch x, then invalidate() to redraw.
}
```

### Why call `invalidate()`?
It asks Android to redraw the view, which re-runs `onDraw` with the new tooltip position.

### Is there a BMI trend graph?
Yes. The BMI card shows one sparkline point per day that has a weight, derived from the constant profile height.

### Why is BMI plotted only on weighted days?
BMI needs a weight, so days without one have no point to plot.

### Why is the tooltip unit-aware?
Each series means something different, so the tooltip labels hours, minutes, kilograms, or a BMI number correctly.

### Why consolidate same-day records?
It avoids duplicate days distorting weekly dashboard trends.

### How are exercise minutes summarized?
Same-day exercise minutes are summed.

### How are sleep and mood summarized?
Same-day sleep and mood values are averaged.

### What happens if height is missing?
BMI is not calculated and a friendly empty state is shown.

### What happens if weight is missing?
BMI is not calculated and the app asks for a recent weight entry.

### How are same-day records consolidated?
`aggregateByDate` groups records into one `DayAggregate` per day: exercise minutes are summed, while sleep, weight, and mood are averaged.

Example:

```kotlin
grouped.entries.map { (date, pairs) ->
    val recs = pairs.map { it.second }
    DayAggregate(
        date = date,
        sleepHours = round1dp(recs.map { it.sleepHours }.average()),
        exerciseMinutes = recs.sumOf { it.exerciseMinutes },
        moodScore = round1dp(recs.map { it.moodScore.toDouble() }.average())
    )
}.sortedBy { it.date }
```

### Why does `aggregateByDate` use `mapNotNull` and `runCatching`?
A record with an unparseable date is dropped instead of crashing the dashboard.

Example:

```kotlin
val date = runCatching { LocalDate.parse(record.recordDate) }.getOrNull()
if (date != null) date to record else null
```

## Android Notifications

### Does the app send notifications?
Yes. It raises a local notification when a new AI insight is generated.

### What is `InsightNotificationScheduler`?
It requests notification permission and registers a repeating alarm that polls for new recommendations.

Example:

```kotlin
alarmManager.setInexactRepeating(
    AlarmManager.RTC_WAKEUP,
    System.currentTimeMillis() + INITIAL_DELAY_MS,
    POLL_INTERVAL_MS,
    pollIntent(context)
)
```

### What is `InsightNotificationReceiver`?
A `BroadcastReceiver` that handles the poll alarm and posts the notification.

### Why `setInexactRepeating`?
Android can batch inexact alarms, which saves battery compared with exact alarms.

### Why is a `PendingIntent` immutable?
`FLAG_IMMUTABLE` stops other apps from modifying the intent, and it is required from Android 12.

Example:

```kotlin
PendingIntent.getBroadcast(
    context, POLL_REQUEST_CODE, intent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)
```

### Why request `POST_NOTIFICATIONS` at runtime?
Android 13 (Tiramisu) and above require the user to grant notification permission explicitly.

Example:

```kotlin
if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PERMISSION_GRANTED) return
activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
```

### What is a notification channel?
A named category, required since Android 8, that controls importance and user settings for a notification.

Example:

```kotlin
val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
manager.createNotificationChannel(channel)
```

## Spring Boot Backend

### What is Spring Boot?
A Java framework for building web APIs quickly.

### What is the main backend package?
`sg.edu.nus.iss.wellness`.

### What does `WellnessApplication` do?
It starts the Spring Boot backend.

### What is a controller?
A class that exposes HTTP endpoints.

Example:

```java
@RestController
@RequestMapping("/api/wellness-records")
class WellnessRecordController {
    @GetMapping
    List<WellnessRecordResponse> list() {
        return List.of();
    }
}
```

### What is a service?
A class that holds business logic.

Example:

```java
@Service
class ChatService {
    ChatResponse askQuestion(AppUser user, String question) {
        return aiServiceClient.chat(user.getId(), question);
    }
}
```

### What is a repository?
A Spring Data interface for database access.

Example:

```java
interface WellnessRecordRepository extends JpaRepository<WellnessRecord, Long> {
    Optional<WellnessRecord> findByIdAndUser(Long id, AppUser user);
}
```

### What is an entity?
A Java class mapped to a database table.

Example:

```java
@Entity
class WellnessRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
}
```

### What is a DTO?
A request or response object used at API boundaries.

Example:

```java
public record LoginRequest(String email, String password) {}
public record LoginResponse(String token, String tokenType) {}
```

### Why use DTOs?
They prevent exposing entity internals directly.

### What does `AuthController` do?
It handles register, login, logout, Google login, and reactivation.

### What does `WellnessRecordController` do?
It exposes CRUD APIs for wellness records.

### What does `ChatController` do?
It exposes chat history and chat message endpoints.

### What does `RecommendationController` do?
It lists and generates recommendations.

### What does `AccountController` do?
It handles profile, export, deactivation, and deletion.

### What does `InternalController` do?
It exposes backend-only endpoints for the Python service.

### What does `StatusController` do?
It exposes basic status information.

### What is `DtoMapper`?
A helper that maps entities to API response DTOs.

Example:

```java
static WellnessRecordResponse wellness(WellnessRecord record) {
    return new WellnessRecordResponse(record.getId(), record.getRecordDate());
}
```

### What is `ApiException`?
A controlled exception with an HTTP status and message.

Example:

```java
throw new ApiException(HttpStatus.NOT_FOUND, "Wellness record not found");
```

### What is `GlobalExceptionHandler`?
It converts errors into consistent JSON responses.

Example response:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Mood score must be between 1 and 5"
}
```

### Why not return stack traces to Android?
They are not user-friendly and can leak implementation details.

## Authentication And Security

### What is JWT?
A signed token that proves the user is authenticated.

Example header:

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### What does the backend put in the JWT?
Claims such as subject, user id, display name, role, issue time, and expiry.

### What validates JWTs?
`JwtAuthenticationFilter` and `JwtService`.

Example:

```java
String token = authHeader.substring("Bearer ".length());
String email = jwtService.extractUsername(token);
```

### Why use stateless JWT?
The backend does not need to store server-side sessions.

### Why is CSRF disabled?
The app uses Authorization headers, not browser cookies.

### What protects non-auth APIs?
Spring Security requires authenticated users with the `USER` role.

Example:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/**").permitAll()
    .anyRequest().hasRole("USER")
)
```

### What is `CurrentUserService`?
A helper that gets the authenticated user from the security context.

Example:

```java
AppUser user = currentUserService.requireCurrentUser();
```

### Why derive user identity from JWT?
Clients should not be trusted to submit their own user id.

### How is password storage handled?
Passwords are hashed with BCrypt.

Example:

```java
String hash = passwordEncoder.encode(rawPassword);
boolean ok = passwordEncoder.matches(rawPassword, hash);
```

### Why not store plain passwords?
Plain passwords are unsafe if the database leaks.

### What is Google SSO used for?
It lets users sign in with Google as an extra login path.

### Does Google SSO replace JWT?
No. The backend verifies Google and still issues the app's own JWT.

### What if a Google-only user has no password?
The database allows `password_hash` to be null for SSO users.

### What is the ownership rule?
Users can only access their own records, chats, and recommendations.

### How is ownership enforced for records?
Queries use both record id and authenticated user.

### What status is returned for missing auth?
Usually `401 Unauthorized`.

### What status is returned for forbidden access?
Usually `403 Forbidden`.

### What roles exist?
`USER` and `PREMIUM_USER`.

Example:

```java
public enum Role {
    USER,
    PREMIUM_USER;

    public String authority() {
        return "ROLE_" + name();   // Spring Security expects the ROLE_ prefix
    }
}
```

### Why does `Role.fromValue` default to `USER`?
An unknown or missing role must never break login, so it falls back to the least-privileged role.

Example:

```java
try {
    return Role.valueOf(value.trim().toUpperCase());
} catch (IllegalArgumentException ex) {
    return USER;   // safe fallback
}
```

### What does `JwtAuthenticationFilter` check besides the signature?
It also checks the account is still enabled, so a deactivated user's existing token stops working immediately.

Example:

```java
if (jwtService.isValid(token, user.getUsername()) && user.isEnabled()) {
    SecurityContextHolder.getContext().setAuthentication(authentication);
}
```

### What happens if the token is malformed?
The filter clears the security context and lets the request continue unauthenticated, which ends as `401`.

Example:

```java
} catch (Exception ex) {
    SecurityContextHolder.clearContext();
}
filterChain.doFilter(request, response);
```

### What is login throttling?
After too many failed attempts, the account is locked for a cooling-off window and further logins return `429`.

### What is `LoginAttemptService`?
An in-memory, per-email failure counter that locks an account once failures exceed the configured threshold.

Example:

```java
attempts.compute(key(email), (k, existing) -> {
    Attempt attempt = (existing == null || existing.expired(now)) ? new Attempt() : existing;
    attempt.failures++;
    if (attempt.failures > maxAttempts) {
        attempt.lockedUntil = now.plus(lockoutDuration);
    }
    return attempt;
});
```

### What are the default lockout settings?
Up to five failures are tolerated; the sixth failure locks the account for 180 seconds. Both values are configurable.

Example:

```properties
app.security.login.max-attempts=5
app.security.login.lockout-seconds=180
```

### Why does a correct password still fail while locked?
The lockout is checked before any credential check, so the window cannot be bypassed by guessing correctly.

### Does a failed login on an unknown email cause a lockout?
No. Only existing, active accounts record a failure, so attackers cannot lock accounts that do not exist.

### Why is the lockout state in memory?
It is per-instance and resets on restart, which is acceptable for this project's scale.

### What clears the lockout counter?
A successful sign-in.

Example:

```java
public void recordSuccess(String email) {
    attempts.remove(key(email));
}
```

### What is `Retry-After`?
A response header telling the client how many seconds to wait before retrying.

### How is a Google ID token verified?
`GoogleTokenVerifier` decodes it against Google's public JWKS and checks the audience and issuer.

Example:

```java
NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS_URI).build();
OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<>(
        "aud", (List<String> aud) -> aud != null && aud.contains(clientId));
```

### What is JWKS?
A published set of public keys used to verify tokens signed by the issuer.

### Why check the `aud` claim?
Without it, a token issued for a different app could be replayed against this backend.

### Why check the issuer?
Only `accounts.google.com` may issue tokens the backend trusts.

### What is Google Sign-In error 10?
`DEVELOPER_ERROR`. The app's signing certificate does not match any SHA-1 registered on the Android OAuth client.

### Why did error 10 appear on some machines but not others?
Debug builds are signed with each developer's own `~/.android/debug.keystore`, so only the machine whose SHA-1 was registered could sign in.

### How was that fixed?
A shared debug keystore is committed and `signingConfigs.debug` points at it, so every debug build has one certificate whose SHA-1 is registered once.

### Is committing a debug keystore a security problem?
No. It uses the standard public debug credentials and never signs a release build.

### Why is the Google Web Client ID committed as a build default?
An empty value crashed the app at launch, and the client ID is public information, not a secret.

### Which client ID does the backend check?
The web client ID, because that is the `aud` claim inside the ID token the Android app receives.

### How does the JWT get signed?
With an HMAC key derived from the configured secret.

Example:

```java
return Jwts.builder()
        .subject(user.getEmail())
        .claims(Map.of("uid", user.getId(), "name", user.getDisplayName(), "role", user.getRole().name()))
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(properties.getJwt().getExpirySeconds())))
        .signWith(secretKey())
        .compact();
```

### Which endpoints are public?
Health/info actuator endpoints, `/api/auth/**`, `/api/internal/**`, and CORS preflight.

Example:

```java
.requestMatchers(HttpMethod.GET, "/", "/actuator/health", "/actuator/info").permitAll()
.requestMatchers("/api/auth/**", "/api/internal/**").permitAll()
.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
.anyRequest().hasRole(Role.USER.name())
```

### If `/api/internal/**` is public, what protects it?
A shared secret header, `X-Internal-Service-Token`, checked on every internal call.

Example:

```java
@GetMapping("/wellness-records")
public List<WellnessRecordResponse> records(@RequestHeader("X-Internal-Service-Token") String token,
                                            @PathVariable Long userId,
                                            @RequestParam(defaultValue = "14") int days) {
    requireInternalToken(token);
    ...
}
```

### Why does the internal API not use JWT?
The Python service acts on behalf of the system, not a logged-in user, so it authenticates with a service token.

### If every API requires `ROLE_USER`, how does a premium user pass?
A `PREMIUM_USER` is granted both authorities, so it satisfies `hasRole("USER")` as well.

Example:

```java
@Override
public Collection<? extends GrantedAuthority> getAuthorities() {
    if (role == Role.PREMIUM_USER) {
        return List.of(
                new SimpleGrantedAuthority(Role.USER.authority()),
                new SimpleGrantedAuthority(Role.PREMIUM_USER.authority()));
    }
    return List.of(new SimpleGrantedAuthority(role.authority()));
}
```

### Why was that fix needed?
Spring Security roles are not hierarchical. `PREMIUM_USER` does not imply `USER`, so premium accounts were locked out of every protected API until both authorities were granted.

### What is `GrantedAuthority`?
A permission string Spring Security checks against rules like `hasRole`.

### Why not make `PREMIUM_USER` a separate rule per endpoint?
Premium is an extra capability, not a separate app; every user reaches the same APIs and only chat routing differs.

### What is the unauthenticated entry point?
A handler that writes the same JSON error shape as every other error instead of an HTML login page.

Example:

```java
.exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authEx) -> {
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getWriter(), body);
}))
```

## Account Privacy

### What privacy features exist?
Export, deactivate, and delete.

Example endpoints:

```text
GET    /api/account/export       download a full JSON copy of the account
POST   /api/account/deactivate   reversible: blocks sign-in, keeps data
DELETE /api/account              permanent erasure of the account and its data
```

### What is the difference between deactivate and delete?
Deactivate is reversible and keeps all data; delete is permanent.

### How does a user reactivate?
Through `POST /api/auth/reactivate` with the correct password.

### Why does a wrong password on delete return 400 instead of 403?
The mobile client treats `401`/`403` as session expiry and would sign the user out instead of showing "wrong password".

### Why is delete `@Transactional`?
The account and all its child rows must be removed together or not at all.

### Why does reactivation record a failed attempt but deactivated login does not?
Reactivation's password gate is a brute-force surface, so it is throttled; ordinary login on an inactive account never accrues a lockout.

## Wellness CRUD

### What does CRUD mean?
Create, Read, Update, Delete.

Example endpoints:

```text
POST   /api/wellness-records
GET    /api/wellness-records
PUT    /api/wellness-records/{id}
DELETE /api/wellness-records/{id}
```

### What table stores wellness records?
`wellness_records`.

### What fields are in a wellness record?
Date, sleep hours, weight, exercise type, exercise minutes, mood score, and notes.

### Why validate weight?
Weight must be positive if provided.

Example:

```java
if (request.weightKg() != null && request.weightKg().signum() <= 0) {
    throw new ApiException(HttpStatus.BAD_REQUEST, "Weight must be positive");
}
```

### Why validate mood score?
Mood must stay in the expected 1 to 5 range.

### Why is record date important?
It represents the day being logged, not just creation time.

### How are records listed?
Newest record dates first.

### How can records be filtered?
The backend supports optional date range parameters.

### Why is update done by id and user?
It prevents editing someone else's record.

### Why is delete done by id and user?
It prevents deleting someone else's record.

## MySQL And JPA

### What is JPA?
A Java persistence API for mapping objects to database rows.

Example:

```java
@ManyToOne(optional = false)
private AppUser user;
```

### What is Hibernate?
The JPA implementation used by Spring Boot.

### What is an entity annotation?
It marks a class as database-backed.

Example:

```java
@Entity
@Table(name = "chat_messages")
class ChatMessage {}
```

### What is a primary key?
A unique row identifier.

### What is a foreign key?
A link from one table to another table.

### What is the `users` table?
It stores account identity and auth metadata.

### What is the `chat_messages` table?
It stores user questions and assistant answers.

### What is the `recommendations` table?
It stores generated recommendation outputs.

### Why does MySQL not store embeddings?
Embeddings belong in Chroma, not the transactional database.

### Why use repositories?
They give clean database access methods without manual SQL for common queries.

### What does `findByIdAndUser` help with?
It combines lookup and ownership checking.

Example:

```java
records.findByIdAndUser(id, currentUser)
    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Not found"));
```

### What is a transaction?
A group of database changes that succeeds or fails together.

Example:

```java
@Transactional
public void deleteAccount(AppUser user) {
    chatMessages.deleteByUser(user);
    users.delete(user);
}
```

## Chatbot Flow

### How does a chat message flow?
Android calls Spring Boot, Spring calls Python RAG, Spring saves the answer.

### Why does Spring fetch recent records?
The AI answer can consider recent wellness context.

### What does `ChatService` do?
It orchestrates chat calls and saves chat messages.

### What does `AiServiceClient` do?
It calls the Python AI service.

### What is a source snippet?
A short piece of knowledge-base text used to ground an answer.

### Why store chat history?
Users can see previous questions and answers.

### What is streaming chat?
The answer is sent as token fragments using Server-Sent Events.

### Why use streaming?
It makes slow local AI responses feel more responsive.

### What is SSE?
Server-Sent Events, a simple one-way streaming protocol over HTTP.

Example frame:

```text
data: {"type":"token","text":"Drink water after waking."}

```

### What frames does streaming use?
Sources first, token frames next, then a done frame.

### What happens if streaming fails midway?
The stream sends an error frame.

### What is `ChatStreamService`?
It proxies the Python token stream to Android through an `SseEmitter`, then saves the assembled answer.

### What is an `SseEmitter`?
A Spring MVC object that keeps the HTTP response open and pushes events to the client.

Example:

```java
SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
executor.execute(() -> runStream(emitter, userId, userRole, question, records, latitude, longitude));
return emitter;
```

### Why are recent records fetched before the background thread starts?
The JPA session and security context live on the request thread, so the data is resolved there and passed in.

### Why run the stream on an executor?
It releases the servlet thread promptly instead of blocking it for the whole generation.

### When is the answer saved?
After the stream completes, so history matches the non-streamed path.

Example:

```java
ChatDtos.ChatResponse saved = chatService.saveStreamedAnswer(
        userId, question, answer.toString().trim(), modelName.toString(), sources);
sendDone(emitter, saved);
emitter.complete();
```

### What is premium weather routing?
Premium exercise/weather questions may use an optional local weather agent.

### What three conditions trigger premium routing?
The user is `PREMIUM_USER`, the premium client is enabled, and the question is classified as exercise-related.

Example:

```java
if (userRole == Role.PREMIUM_USER
        && premiumAiClient.isEnabled()
        && intentClassifier.isExerciseRelated(question)) {
    usedPremium = premiumAiClient.premiumStreamChat(...);
}
if (!usedPremium) {   // standard path, also the fallback
    aiServiceClient.streamChat(...);
}
```

### What is `ExerciseIntentClassifier`?
A keyword classifier that decides whether a question is about exercise, weather, or outdoor activity.

Example:

```java
private static final Set<String> KEYWORDS = Set.of(
        "exercise", "workout", "run", "outdoor", "weather", "wbgt", "heat", ...);
```

### Why word-boundary matching?
`\b(...)\b` matches whole words only, so "run" does not fire on "grunt" or "shrunk".

### Why a keyword classifier instead of an LLM?
It is instant, free, deterministic, and easy to explain in a demo.

### What if the premium agent fails?
The backend falls back to standard RAG chat.

### What is `StreamForwardedException`?
A marker exception meaning an upstream error frame was already relayed, so the emitter just closes cleanly.

Example:

```java
} catch (StreamForwardedException forwarded) {
    emitter.complete();   // client already saw the error frame
} catch (Exception exception) {
    sendError(emitter, "Chatbot unavailable. Please retry when services are running.");
    emitter.complete();
}
```

## Premium Weather Agent

### What is the premium server?
A separate FastAPI service running a LangChain tool-calling agent with weather tools.

### What tools does the agent have?
Current WBGT (wet bulb globe temperature) and the 2-hour weather forecast.

### Where does the weather data come from?
Singapore's public `data.gov.sg` real-time weather API.

### What is WBGT?
A heat-stress measure combining temperature, humidity, wind, and radiation, used to judge outdoor exercise safety.

### What are the safety bands?
NEA advisory zones from white (safe) through green, yellow, brown, to black (suspend outdoor exercise).

Example:

```python
SAFETY_BANDS = [
    (33.0, "DANGER - BLACK Zone (>= 33 C). Suspend all outdoor exercise."),
    (31.0, "ALERT - BROWN Zone (31 to 32.9 C). Minimise strenuous outdoor activity."),
    (29.0, "ADVISORY - YELLOW Zone (29 to 30.9 C). Reduce outdoor intensity."),
    (27.0, "SAFE - GREEN Zone (27 to 28.9 C). Outdoor exercise is generally safe."),
    (0.0,  "SAFE - WHITE Zone (< 27 C). Low heat stress."),
]
```

### How does the agent pick a weather station?
It uses the haversine formula to find the station closest to the user's GPS coordinates.

Example:

```python
def haversine(user_lat, user_lon, station_lat, station_lon):
    rad = 6371.0  # Earth radius in km
    dLat = (station_lat - user_lat) * math.pi / 180.0
    dLon = (station_lon - user_lon) * math.pi / 180.0
    a = pow(math.sin(dLat / 2), 2) + pow(math.sin(dLon / 2), 2) * math.cos(user_lat) * math.cos(station_lat)
    return rad * 2 * math.asin(math.sqrt(a))
```

### Why are tools rebuilt on every request?
So the user's GPS coordinates are injected directly instead of letting the LLM guess a location.

Example:

```python
tools = [get_wet_bulb_temperature(lat, lon), get_weather_forecast(lat, lon)]
agent = create_tool_calling_agent(llm, tools, prompt)
```

### What is a tool-calling agent?
An LLM that can choose to call declared functions, read their results, and use them in its answer.

### What is `AgentExecutor`?
The LangChain loop that runs the agent, executes chosen tools, and feeds results back.

Example:

```python
executor = AgentExecutor(
    agent=agent,
    tools=tools,
    max_iterations=2,          # stop runaway tool-calling loops
    max_execution_time=180,    # hard kill; allows a cold Ollama model load
    return_intermediate_steps=True,
)
```

### Why cap `max_iterations`?
Without it, an agent can loop calling tools forever.

### What is `agent_scratchpad`?
The prompt slot where LangChain records prior tool calls and results for the next step.

### Why sanity-bound WBGT readings?
Values outside a plausible range for Singapore indicate a bad reading and are filtered out.

### What if the user has no location?
The agent says it can only give weather for locations it has data for.

## Python AI Service

### What framework does the Python service use?
FastAPI.

### What does `/health` do?
It returns a simple service health response.

Example:

```python
@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "UP"}
```

### What does `/rag/reindex` do?
It rebuilds the Chroma vector index from the knowledge base.

### What does `/rag/chat` do?
It returns a non-streamed RAG answer.

Example:

```python
@app.post("/rag/chat")
async def chat(request: RagChatRequest) -> RagChatResponse:
    return await rag.chat(request)
```

### What does `/rag/chat/stream` do?
It streams a RAG answer as SSE frames.

### What does `/agent/recommendation/{user_id}` do?
It generates and saves a recommendation for a user.

### What does `RagService` do?
It indexes knowledge, retrieves chunks, builds prompts, and calls Ollama.

Example:

```python
retrieved = await self.retrieve(request.question)
prompt = self._build_prompt(request, retrieved)
answer = await self.ollama.generate(prompt, num_predict=220)
```

### What does `AgentService` do?
It chooses a recommendation focus and generates personalized advice.

Example:

```python
records = await self.backend.recent_records(user_id)
focus = self._choose_focus(records)
chunks = await self.rag.retrieve(focus)
```

### What does `BackendClient` do?
It calls Spring internal APIs for recent records and recommendation saving.

### What does `OllamaClient` do?
It calls Ollama for embeddings and generation.

### What does `knowledge_base.py` do?
It loads Markdown files and splits them into chunks.

Example:

```python
for path in sorted(root.glob("*.md")):
    raw = path.read_text(encoding="utf-8")
    title = _extract_title(raw, path.stem.title())
```

### What does `models.py` contain?
Pydantic request and response models.

Example:

```python
class RagChatRequest(BaseModel):
    userId: int
    question: str
    recentRecords: list[RecentRecord] = []
```

### What does `config.py` contain?
Environment-driven settings.

### What does `tracing.py` do?
It configures optional LangSmith tracing.

### Is LangSmith required?
No. It is optional tracing and disabled by default.

### Why is FastAPI useful here?
It is simple, async-friendly, and good for JSON APIs.

### Why does `OllamaClient` try two embedding endpoints?
Newer Ollama serves `/api/embed`; older versions only serve `/api/embeddings`. A `404` on the first triggers a fallback to the second.

Example:

```python
try:
    response = await client.post(f"{base}/api/embed", json={"model": model, "input": text})
    self._raise_for_status(response)
    return response.json()["embeddings"][0]
except httpx.HTTPStatusError as first_error:
    if first_error.response.status_code != 404:
        raise
    response = await client.post(f"{base}/api/embeddings", json={"model": model, "prompt": text})
```

### Why re-raise anything that is not a 404?
A `500` or auth failure is a real error, not a missing endpoint, so it must not be masked by the fallback.

### Why does `_raise_for_status` include the response body?
An Ollama error body explains the cause, and the default `raise_for_status` message omits it.

## Ollama Performance Tuning

### Why does performance need tuning at all?
The production droplet has no GPU, so all inference is CPU-only and latency is the main demo risk.

### What is `num_ctx`?
The context-window size. It is capped at 1024 so prefill stays cheap on CPU.

Example:

```python
"options": {
    "num_predict": num_predict,
    "temperature": 0.3,
    # Smaller KV cache speeds up local CPU inference.
    "num_ctx": 1024,
}
```

### What is `num_predict`?
The maximum number of tokens generated. Bounding it stops long, slow answers.

### What is a KV cache?
Stored attention keys and values for earlier tokens; a smaller context means a smaller, faster cache.

### What is `OLLAMA_KEEP_ALIVE=-1`?
It keeps model weights resident in memory forever, removing the cold-start delay on the first chat.

Example:

```yaml
OLLAMA_KEEP_ALIVE: "-1"
OLLAMA_NUM_PARALLEL: "1"
OLLAMA_MAX_LOADED_MODELS: "2"
```

### Why `OLLAMA_NUM_PARALLEL=1`?
One request at a time gets all CPU cores, which is faster than splitting cores across concurrent requests.

### Why `OLLAMA_MAX_LOADED_MODELS=2`?
The generation model and the embedding model both stay loaded, since RAG needs both on every question.

### Why warm the model at the end of a deploy?
So the first user of the demo does not pay the model-load cost.

### Why was `top_k` lowered from 4 to 3?
Fewer retrieved chunks means a shorter prompt and faster prefill, with little loss of grounding.

### Why pin the embedding model tag?
An unpinned tag can resolve to a different model, which would silently invalidate every stored embedding.

### Why must embeddings be rebuilt if the model changes?
Vectors from different models are not comparable, so retrieval would return nonsense.

### Why prebuild the RAG index during deploy?
The first question would otherwise pay for a full reindex before it could answer.

### Why is the generation model small?
`qwen2.5:1.5b` answers in seconds on CPU, where a larger model would take far too long for a live demo.

### What is the trade-off of a small model?
Lower answer quality, which RAG grounding partly compensates for.

## Observability And Tracing

### What is LangSmith?
A hosted tracing dashboard for LLM applications.

### How is tracing added to a function?
With the `@traceable` decorator, tagging the run type so the dashboard groups it correctly.

Example:

```python
# Each decorator sits on a different method.
@traceable(run_type="embedding", name="ollama.embed", process_inputs=strip_self)
async def embed(self, text: str) -> list[float]: ...

@traceable(run_type="retriever", name="rag.retrieve", process_inputs=strip_self)
async def retrieve(self, question: str, top_k: int = 3) -> list[KnowledgeChunk]: ...
```

### What run types are traced?
`embedding` for embed calls, `llm` for generation, `retriever` for retrieval, and `chain` for the whole RAG chat.

### What is `strip_self`?
A helper that drops the bound `self` argument so the trace does not log the whole service object.

### How is a streamed answer traced?
A `reduce_fn` joins the token fragments so LangSmith stores one combined answer instead of hundreds of rows.

Example:

```python
@traceable(
    run_type="llm",
    name="ollama.generate.stream",
    reduce_fn=lambda fragments: "".join(fragments),
)
```

### What happens if tracing is enabled without an API key?
The service logs a warning and disables tracing rather than failing.

Example:

```python
logger.warning("LANGSMITH_TRACING is enabled but LANGSMITH_API_KEY is empty; tracing disabled.")
os.environ["LANGSMITH_TRACING"] = "false"
```

### Why is tracing off by default?
It is optional evidence, needs a key, and should never be a hard dependency of the demo.

## RAG Fundamentals

### What does RAG mean?
Retrieval-Augmented Generation.

Example flow:

```text
Question -> Embed question -> Retrieve chunks -> Build prompt -> Generate answer
```

### Why use RAG?
The model answers using retrieved project knowledge instead of only memory.

### What is the knowledge base?
Curated Markdown files under `rag-knowledge-base/`.

### What topics are in the knowledge base?
Sleep, exercise, stress, hydration, nutrition, habits, BMI, and body metrics.

### What is indexing?
Loading documents, chunking text, embedding chunks, and saving vectors.

Example:

```python
embedding = await ollama.embed(chunk.text)
collection.add(ids=[chunk.id], embeddings=[embedding], documents=[chunk.text])
```

### What is a chunk?
A small text section used for retrieval.

### What is an embedding?
A numeric vector representing text meaning.

### Why use embeddings?
They help find text similar to the user's question.

### What is top-k retrieval?
Returning the best few matching chunks.

Example:

```python
results = collection.query(query_embeddings=[embedding], n_results=3)
```

### Why not retrieve everything?
Large prompts are slower and less focused.

### What is the grounded prompt?
A prompt that includes retrieved context and instructions.

Example:

```text
Use only the retrieved context.
Retrieved context:
Source: Sleep Hygiene
Keep a regular bedtime.
User question:
How can I sleep better?
```

### Why include recent wellness records?
They make answers more personal while still educational.

### Why include source snippets?
They show what context grounded the answer.

### What if no chunks match well?
The chatbot should answer cautiously and mention limited context.

### What should the chatbot avoid?
Diagnosis, treatment claims, emergency advice, and medical prescriptions.

### Why add BMI RAG content?
The dashboard has BMI features, so users may ask BMI-related questions.

### How should BMI be explained?
As a screening number based on height and weight, not a diagnosis.

### Why skip boilerplate in snippets?
It gives the model useful guidance instead of repeated disclaimers.

### What does the retrieval code actually return?
Chunk ids, metadata, and documents, which are rebuilt into `KnowledgeChunk` objects.

Example:

```python
embedding = await self.ollama.embed(question)
results = self.collection.query(query_embeddings=[embedding], n_results=top_k)
for index, chunk_id in enumerate(results.get("ids", [[]])[0]):
    metadata = results.get("metadatas", [[]])[0][index] or {}
    document = results.get("documents", [[]])[0][index] or ""
```

### What happens on the very first query?
If the collection is empty, the service reindexes before retrieving.

Example:

```python
if self.collection.count() == 0:
    await self.reindex()
```

### Why are snippets truncated in the prompt?
A smaller grounded prompt keeps CPU prefill fast.

Example:

```python
context = "\n\n".join(f"Source: {chunk.title}\n{chunk.snippet[:200]}" for chunk in chunks)
```

### What guardrails are in the RAG prompt?
Use only retrieved context, no diagnosis or treatment, refuse off-topic questions, and keep answers concise.

Example:

```text
Use only the retrieved context and recent wellness records.
Do not diagnose, prescribe treatment, or provide emergency advice.
If a question is outside wellness habits, say the app only supports wellness habit questions.
```

### How are recent records formatted into the prompt?
As one compact line per day.

Example:

```python
records = "\n".join(
    f"- {record.recordDate}: sleep {record.sleepHours}h, exercise {record.exerciseType or 'none'} "
    f"{record.exerciseMinutes}min, mood {record.moodScore}/5"
    for record in request.recentRecords
) or "No recent wellness records were provided."
```

### What happens if the model produces no tokens?
The stream emits a single fallback token so the user never sees an empty answer.

Example:

```python
if not produced:
    yield _sse({"type": "token", "text": "I could not generate a response right now. Please try again."})
```

## Agentic Recommendation Workflow

### What makes the recommendation agentic?
It follows a workflow: fetch records, analyze trends, choose focus, retrieve context, generate, save.

### What data does the agent analyze?
Recent sleep, exercise, and mood records.

### What if there are fewer than three records?
The focus becomes consistent wellness tracking.

### What if average sleep is below 7 hours?
The focus becomes sleep consistency.

### What if exercise appears on fewer than three days?
The focus becomes light activity routine.

### What if average mood is low?
The focus becomes stress and mood support.

### What if records look balanced?
The focus becomes maintaining balanced habits.

### What does the focus rule chain look like?
An ordered set of checks where the first match wins.

Example:

```python
def _choose_focus(self, records: list[dict]) -> str:
    if len(records) < 3:
        return "consistent wellness tracking"
    if average_sleep < 7:
        return "sleep consistency"
    if exercise_days < 3:
        return "light activity routine"
    if average_mood <= 2:
        return "stress and mood support"
    return "maintaining balanced habits"
```

### Why does rule order matter?
The first matching rule wins, so the most important gap is addressed first.

### Why use deterministic rules before LLM output?
They make the recommendation explainable and reliable.

### What is the full agent workflow in code?
Fetch records, choose focus, summarize trends, retrieve context, generate, then save through the backend.

Example:

```python
records = await self.backend.recent_records(user_id)
focus = self._choose_focus(records)
trend_summary = self._trend_summary(records, focus)
chunks = await self.rag.retrieve(focus)
generated = await self.chain.ainvoke({"focus": focus, "trend_summary": trend_summary, "context": context})
title, recommendation_text, action_items = self._parse_generated(generated, focus)
saved = await self.backend.save_recommendation(user_id, InternalRecommendationRequest(...))
```

### Why does the agent retrieve using the focus, not the question?
There is no user question; the chosen focus is the retrieval query.

### How is the LLM output parsed?
The prompt asks for a fixed shape, then the parser reads the title line and the bullet action items.

Example:

```python
for line in lines:
    if line.lower().startswith("title:"):
        title = ...
    elif line.startswith("-"):
        action_items.append(...)
if len(action_items) < 3:
    ...   # top up with fallback items
```

### What does LangChain do here?
It connects the prompt template, Ollama model, and output parser.

Example:

```python
self.chain = self.prompt_template | self.llm | StrOutputParser()
generated = await self.chain.ainvoke(payload)
```

### Where is the recommendation saved?
Spring Boot saves it into MySQL.

### Why does Python not write to MySQL directly?
Spring Boot owns persistence and business rules.

### What happens if generated text is incomplete?
Fallback title and action items are used.

## Docker And Operations

### Why use Docker Compose?
It starts backend/runtime services consistently.

Example:

```yaml
services:
  mysql:
    image: mysql:8.4
  spring-backend:
    build: ./spring-backend
```

### Which services are in the main stack?
MySQL, Spring Boot, Python AI service, Ollama, and related volumes.

### Is Android in Docker?
No. Android runs through Android Studio, emulator, or device.

### Why persist MySQL data in a volume?
So database data survives container restarts.

### Why persist Ollama data?
So models do not need to be downloaded repeatedly.

### Why persist Chroma data?
So vector indexes can survive restarts.

### What is `.env.example` for?
It documents required environment variables without real secrets.

Example:

```dotenv
MYSQL_DATABASE=wellness
OLLAMA_GENERATION_MODEL=qwen2.5:1.5b
```

### Why not commit real `.env` files?
They may contain secrets.

### What is Caddy used for?
TLS and reverse proxy in deployment.

### Why Caddy instead of nginx?
It obtains and renews HTTPS certificates automatically, with no manual certificate steps.

### What does the Caddyfile do?
It reverse-proxies the API domain to the Spring backend and adds security headers.

Example:

```caddyfile
{$API_DOMAIN} {
    reverse_proxy spring-backend:8080

    header {
        Strict-Transport-Security "max-age=31536000; includeSubDomains"
        X-Content-Type-Options "nosniff"
        X-Frame-Options "DENY"
        -Server
    }
}
```

### What is HSTS?
`Strict-Transport-Security` tells browsers to use HTTPS only for this host.

### What does `X-Content-Type-Options: nosniff` do?
It stops the browser guessing a response's content type.

### Why remove the `Server` header?
It avoids advertising the server software to attackers.

### What is Terraform used for?
Provisioning infrastructure.

### What does the Terraform config create?
One DigitalOcean droplet with a reserved IP, a firewall opening only 22, 80, and 443, DNS, and a project.

### What is remote state?
Terraform's record of provisioned infrastructure, stored on DO Spaces so the whole team shares one view.

### Why a reserved IP?
The address survives rebuilding the droplet, so DNS does not need to change.

### What is DuckDNS?
A free dynamic-DNS host, used to get an HTTPS hostname without buying a domain.

### What is Ansible used for?
Configuring and deploying the server.

### Why move deployment from raw SSH scripts to Ansible?
Ansible tasks are idempotent and declarative, so re-running a deploy converges instead of repeating side effects.

### What is idempotent?
Running it twice leaves the same result as running it once.

### How do images reach the server?
GitHub Actions builds and pushes them to GHCR, then the deploy step pulls the prod overlay on the droplet.

### What is a Compose overlay?
A second Compose file layered on the base one, so production can differ without duplicating the whole stack.

### What does the prod overlay change?
It keeps services internal-only, turns Adminer off, adds Caddy, and enforces memory limits.

### Why enforce `mem_limit`?
Ollama and MySQL together can exhaust the droplet, so limits stop one service killing the others.

### Why did Chroma need a volume permission fix?
The container user could not write to the mounted host directory, so the vector index failed to persist.

### What is SonarQube used for?
Code quality and coverage evidence.

### What is JaCoCo?
The Java coverage tool whose report SonarQube reads.

### What is a code smell?
Maintainable-but-poor code that SonarQube flags, such as duplication or an over-complex method.

### What is a security hotspot?
Code that needs a human to confirm whether it is safe, rather than a definite vulnerability.

### What is Codex Security used for?
Security review evidence for sensitive changes.

### What is Semgrep Supply Chain?
A scanner that finds vulnerable dependencies.

### Why commit lockfiles for every ecosystem?
Semgrep resolves dependencies from the lockfile without running a build.

### What lockfiles exist?
`gradle.lockfile` for Android, `packages.lock.json` for .NET, and `maven_dep_tree.txt` for Spring Boot.

### What does the CI `lockfiles` job do?
It regenerates each lockfile and fails if the result drifts from what is committed.

### Why pin GitHub Actions to commit SHAs?
A mutable tag like `@v4` can be repointed at malicious code; a SHA cannot.

### What is Dependabot?
A bot that opens pull requests to update dependencies.

### Why avoid live LLM generation in CI?
It is slow, flaky, and needs local model setup.

### Why were scheduled workflows disabled?
To save GitHub Actions minutes; CI runs on pull requests instead.

### What is the OpenWiki workflow?
An automated job that regenerates the `openwiki/` documentation.

## Testing And Validation

### What backend tests are important?
Auth, ownership, CRUD, chat, recommendations, and error handling.

### What Android tests are important?
Dashboard helper tests, parsing tests, and manual UI checks.

### What Python tests are important?
RAG indexing, retrieval, edge cases, Ollama client, and agent rules.

### Why mock Ollama in tests?
CI should not depend on downloaded models or slow generation.

### What is a smoke test?
A small test that checks the main path works.

### What is an integration test?
A test involving multiple real services.

### Why are live Ollama tests optional?
They require a running Ollama instance and local models.

### What should be shown in the demo?
Login, CRUD, RAG chat, recommendation generation, and Docker/runtime evidence.

### Why include source snippets in validation?
They prove RAG retrieval happened.

### What is traceability?
Linking code changes to requirement IDs, task IDs, specs, and tests.

## Code Fundamentals

### What is Kotlin?
A modern language used for Android development.

### What is Java?
The language used for the Spring Boot backend.

### What is Python?
The language used for AI service orchestration.

### What is C#?
The language used by the optional .NET backup and desktop client.

### What is the .NET backup backend?
A second implementation of the same API contract, kept as cold standby and bonus evidence.

### Why does a backup backend exist at all?
It proves the API contract is implementation-independent, since Android can point at either backend.

### Does the backup backend share the lockout state?
No. Each backend throttles independently, which is fine because they front the same clients.

### What is the desktop client?
An optional Avalonia (.NET) client that satisfies `REQ-21`, consuming the same Spring Boot REST API as Android.

### Where does the desktop client store its JWT?
In memory only, so nothing is written to disk.

### What is Avalonia?
A cross-platform .NET UI framework.

### What is XML in Android?
A layout format for defining UI screens.

Example:

```xml
<TextView
    android:id="@+id/titleText"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="Wellness" />
```

### What is an Activity?
An Android screen with lifecycle methods.

Example:

```kotlin
class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
```

### What is a lifecycle?
The states an Activity goes through, such as create, start, pause, and destroy.

### Why avoid network calls on the main thread?
They can freeze the UI.

### What is a callback?
A function passed to be called later.

Example:

```kotlin
val onDelete: (WellnessRecordResponse) -> Unit = { record ->
    deleteRecord(record.id)
}
```

### What is a lambda?
A small inline function.

Example:

```kotlin
records.map { record -> record.recordDate }
```

### What is async programming?
Running work without blocking the caller.

Example:

```kotlin
lifecycleScope.launch {
    val result = api.recommendations()
    render(result)
}
```

### What is JSON?
A text format for API request and response data.

Example:

```json
{
  "email": "asha@example.com",
  "password": "Password123!"
}
```

### What is REST?
A common API style using HTTP methods and URLs.

Example:

```text
GET /api/wellness-records
POST /api/chat/messages
```

### What is HTTP status 200?
Success.

### What is HTTP status 201?
Created successfully.

### What is HTTP status 204?
Success with no response body.

### What is HTTP status 400?
Bad request or validation error.

### What is HTTP status 401?
Authentication is missing or invalid.

### What is HTTP status 403?
The user is authenticated but not allowed.

### What is HTTP status 404?
The requested resource was not found.

### What is HTTP status 500?
Unexpected server error.

### What is HTTP status 502?
A dependency service failed or could not be reached.

### What is dependency injection?
Passing dependencies into classes instead of creating everything inside them.

Example:

```java
public ChatService(AiServiceClient aiServiceClient,
                   ChatMessageRepository chatMessages) {
    this.aiServiceClient = aiServiceClient;
    this.chatMessages = chatMessages;
}
```

### Why use dependency injection?
It improves testability and separation of concerns.

### What is separation of concerns?
Each class has a focused responsibility.

### What is validation?
Checking input before using or saving it.

Example:

```java
public record LoginRequest(
    @Email String email,
    @NotBlank String password
) {}
```

### What is serialization?
Converting objects to JSON or another transport format.

Example:

```kotlin
val request = LoginRequest("asha@example.com", "Password123!")
// Gson sends it as JSON in the HTTP body.
```

### What is deserialization?
Converting JSON back into objects.

Example:

```kotlin
val response: LoginResponse = api.login(request)
val token = response.token
```

### What is logging?
Recording useful runtime information for debugging.

Example:

```java
LOGGER.info("Backend internal API base URL: {}", backendBaseUrl);
```

### Why avoid logging secrets?
Logs may be shared or stored and should not expose credentials.

### What is a model class?
A class representing data.

Example:

```kotlin
data class ChatResponse(
    val question: String,
    val answer: String
)
```

### What is a helper class?
A class with reusable supporting logic.

### What is a unit test?
A small test for one class or function.

Example:

```python
def test_extract_title_prefers_first_markdown_h1():
    assert _extract_title("# Sleep", "Fallback") == "Sleep"
```

### What is mocking?
Replacing a real dependency with a fake one in tests.

Example:

```python
class FakeOllama:
    async def generate(self, prompt: str, num_predict: int = 220) -> str:
        return "Deterministic test answer."
```

### What is a build tool?
A tool that compiles code, runs tests, and packages the app.

### What build tool does Android use?
Gradle.

### What build tool does Spring Boot use?
Maven.

### What does `pytest` do?
It runs Python tests.

## Demo Defense Questions

### Why did the team choose local AI?
It satisfies the free/local AI requirement and avoids paid LLM APIs.

### Why is Spring Boot required?
It is part of the assignment requirement.

### Why is MySQL required?
The assignment requires persistent backend storage.

### Why use Docker?
It makes backend services easier to run consistently.

### Why use JWT?
It protects APIs without server-side sessions.

### Why use RAG instead of simple prompts?
RAG grounds answers in curated wellness content.

### Why use a curated knowledge base?
It is reliable for demo and avoids unsafe document upload complexity.

### Why save chat messages?
It gives history and proves backend persistence.

### Why save recommendations?
Users need to view generated advice later.

### Why use author comments?
The assignment requires author attribution in classes or key methods.

### What is the most important security rule?
Users must not access another user's data.

### What is the most important architecture rule?
Android must only call Spring Boot.

### What is the biggest AI limitation?
The app gives general wellness education, not medical diagnosis.

### What is the biggest demo risk?
Local AI can be slow if models are not pulled or warmed.

### How is that risk reduced?
Use small models, bounded prompts, streaming, and local setup checks.

### What should be done before recording the demo?
Start Docker, pull models, seed data, run checks, and rehearse the flow.

### How was chatbot latency actually reduced?
A smaller model, a warm resident model, a capped context window, fewer retrieved chunks, and streaming so the first token appears early.

### Which of those changes helps the most perceptually?
Streaming, because the user sees words immediately instead of waiting for the whole answer.

### Is the app deployed anywhere?
Yes. A DigitalOcean droplet runs the stack behind Caddy with automatic HTTPS.

### How is supply-chain security evidenced?
Committed lockfiles for every ecosystem, Semgrep Supply Chain scanning, Dependabot, and workflow actions pinned to commit SHAs.

### What is spec-driven development here?
Specs under `docs/specs/` are the contract; code changes update the owning spec and cite requirement and task IDs.

## Quick One-Line Answers

### Retrofit?
Android HTTP client wrapper around annotated Kotlin interfaces.

### OkHttp?
The HTTP engine used under Retrofit.

### Gson?
JSON converter for API data.

### Adapter?
Connects list data to list row views.

### ViewHolder?
Caches row view references for performance.

### JWT?
Signed token proving authenticated identity.

### JPA?
Maps Java entities to database tables.

### Repository?
Database access interface.

### DTO?
API request or response shape.

### RAG?
Retrieve relevant context, then generate an answer.

### Embedding?
Numeric representation of text meaning.

### Chroma?
Vector database for retrieved chunks.

### Ollama?
Local model runtime.

### FastAPI?
Python web framework for AI endpoints.

### SSE?
Server-to-client streaming events.

### Docker Compose?
Runs multiple local services together.

### SonarQube?
Quality dashboard.

### BCrypt?
Secure password hashing algorithm.

### CSRF?
Browser cookie attack; less relevant for stateless bearer-token APIs.

### BMI?
Screening number from weight and height, not a diagnosis.

### SseEmitter?
Spring object that holds the response open and pushes SSE frames.

### Sealed interface?
A closed set of subtypes, so `when` handling is exhaustive.

### Interceptor?
OkHttp hook that inspects or rewrites every request and response.

### Role?
`USER` or `PREMIUM_USER`, carried as a JWT claim.

### Login lockout?
Too many failed attempts locks the account for a cooling-off window and returns `429`.

### 429?
Too Many Requests, used for the login lockout.

### Retry-After?
Header telling the client how long to wait before retrying.

### JWKS?
Public keys published by an issuer so its tokens can be verified.

### X-Internal-Service-Token?
Shared secret protecting the backend's internal API from anyone but the Python service.

### WBGT?
Heat-stress measure used to judge whether outdoor exercise is safe.

### Haversine?
Great-circle distance formula, used to find the nearest weather station.

### Tool-calling agent?
An LLM that can call declared functions and use their results.

### AgentExecutor?
LangChain loop that runs the agent and executes its chosen tools.

### Notification channel?
Required category controlling a notification's importance since Android 8.

### PendingIntent?
A deferred intent another component fires on your app's behalf.

### GrantedAuthority?
A permission string Spring Security matches against `hasRole` rules.

### num_ctx?
Context-window size; capped at 1024 for cheap CPU prefill.

### num_predict?
Cap on how many tokens the model generates.

### OLLAMA_KEEP_ALIVE=-1?
Keeps model weights resident so there is no cold start.

### Caddy?
Reverse proxy with automatic HTTPS certificates.

### HSTS?
Header telling browsers to use HTTPS only for this host.

### Terraform?
Provisions the droplet, firewall, DNS, and reserved IP.

### Ansible?
Idempotent server configuration and deployment.

### GHCR?
GitHub Container Registry, where deploy images are pushed.

### Compose overlay?
A second Compose file layered over the base for production differences.

### DuckDNS?
Free dynamic-DNS hostname used to get HTTPS without buying a domain.

### JaCoCo?
Java coverage tool whose report SonarQube reads.

### Security hotspot?
Code needing human review to decide whether it is safe.

### Semgrep Supply Chain?
Scans committed lockfiles for vulnerable dependencies.

### Lockfile?
Pinned dependency versions, letting scanners resolve deps without a build.

### Error 10?
Google Sign-In `DEVELOPER_ERROR`: the signing SHA-1 is not registered.

### Avalonia?
Cross-platform .NET UI framework used by the optional desktop client.

### @traceable?
LangSmith decorator that records a function call as a trace run.
