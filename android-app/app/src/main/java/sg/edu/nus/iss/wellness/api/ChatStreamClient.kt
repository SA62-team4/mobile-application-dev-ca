package sg.edu.nus.iss.wellness.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import sg.edu.nus.iss.wellness.BuildConfig
import sg.edu.nus.iss.wellness.TokenStore
import java.util.concurrent.TimeUnit

/**
 * One Server-Sent Events frame from the chat streaming endpoint.
 *
 * @author Tiong Zhong Cheng
 */
sealed interface ChatStreamEvent {
    /** Retrieved knowledge-base sources, delivered once before the tokens. */
    data class Sources(val sources: List<SourceSnippet>) : ChatStreamEvent

    /** One generated answer fragment; concatenate in order to form the full answer. */
    data class Token(val text: String) : ChatStreamEvent

    /** Terminal frame after the answer is persisted, carrying its saved id and metadata. */
    data class Done(
        val id: Long,
        val modelName: String?,
        val createdAt: String?,
        val sources: List<SourceSnippet>
    ) : ChatStreamEvent

    /** Terminal frame signalling the stream failed; [message] is user-facing. */
    data class Error(val message: String) : ChatStreamEvent
}

/**
 * Streams chatbot answers from the backend SSE endpoint using a raw OkHttp call, since
 * Retrofit delivers a parsed body only once the response completes. Each parsed
 * [ChatStreamEvent] is delivered on the main thread via [onEvent] as it arrives.
 *
 * The JWT is attached per call and 401/403 clears the stored token so the caller can
 * return to login, mirroring [ApiClient]'s interceptor behaviour.
 *
 * @author Tiong Zhong Cheng
 */
class ChatStreamClient(private val tokenStore: TokenStore) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // Read timeout bounds the gap between tokens, not the whole answer; the overall
        // call is left untimed (callTimeout 0) so long CPU-only generations can finish.
        .readTimeout(180, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /**
     * Open the stream for [question] and invoke [onEvent] (on the main thread) for every
     * frame. Suspends until the stream ends. Network/HTTP failures surface as a final
     * [ChatStreamEvent.Error]; the caller does not need a separate try/catch, though
     * cancelling the coroutine aborts the call.
     */
    suspend fun stream(question: String, onEvent: suspend (ChatStreamEvent) -> Unit) {
        val token = tokenStore.token()
        val body = gson.toJson(ChatRequest(question)).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL + "api/chat/messages/stream")
            .apply { if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer $token") }
            .addHeader("Accept", "text/event-stream")
            .post(body)
            .build()

        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) {
                    tokenStore.clear()
                    emit(onEvent, ChatStreamEvent.Error("Session expired. Please sign in again."))
                    return@use
                }
                val source = response.body?.source()
                if (!response.isSuccessful || source == null) {
                    emit(onEvent, ChatStreamEvent.Error("Chatbot unavailable. Please retry."))
                    return@use
                }
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.substring("data:".length).trim()
                    if (payload.isEmpty()) continue
                    val event = parse(payload) ?: continue
                    emit(onEvent, event)
                    // Stop after terminal frames.
                    if (event is ChatStreamEvent.Done || event is ChatStreamEvent.Error) break
                }
            }
        }
    }

    private suspend fun emit(onEvent: suspend (ChatStreamEvent) -> Unit, event: ChatStreamEvent) {
        withContext(Dispatchers.Main) { onEvent(event) }
    }

    private fun parse(payload: String): ChatStreamEvent? {
        val node = runCatching { gson.fromJson(payload, JsonObject::class.java) }.getOrNull() ?: return null
        return when (node.get("type")?.asString) {
            "sources" -> ChatStreamEvent.Sources(readSources(node))
            "token" -> ChatStreamEvent.Token(node.get("text")?.asString.orEmpty())
            "done" -> ChatStreamEvent.Done(
                id = node.get("id")?.asLong ?: 0L,
                modelName = node.get("modelName")?.takeIf { !it.isJsonNull }?.asString,
                createdAt = node.get("createdAt")?.takeIf { !it.isJsonNull }?.asString,
                sources = readSources(node)
            )
            "error" -> ChatStreamEvent.Error(
                node.get("message")?.asString ?: "Chatbot unavailable. Please retry."
            )
            else -> null
        }
    }

    private fun readSources(node: JsonObject): List<SourceSnippet> {
        val array = node.getAsJsonArray("sources") ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element.asJsonObject
            SourceSnippet(
                title = obj.get("title")?.asString.orEmpty(),
                snippet = obj.get("snippet")?.asString.orEmpty()
            )
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
