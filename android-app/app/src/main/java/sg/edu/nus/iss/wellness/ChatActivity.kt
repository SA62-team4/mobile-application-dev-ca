package sg.edu.nus.iss.wellness

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import sg.edu.nus.iss.wellness.adapter.ChatAdapter
import sg.edu.nus.iss.wellness.api.ApiClient
import sg.edu.nus.iss.wellness.api.ApiService
import sg.edu.nus.iss.wellness.api.ChatResponse
import sg.edu.nus.iss.wellness.api.ChatStreamClient
import sg.edu.nus.iss.wellness.api.ChatStreamEvent
import sg.edu.nus.iss.wellness.api.SourceSnippet
import sg.edu.nus.iss.wellness.databinding.ActivityChatBinding
import sg.edu.nus.iss.wellness.ui.addStateBlock
import sg.edu.nus.iss.wellness.ui.apiErrorMessage
import sg.edu.nus.iss.wellness.ui.highlightTab
import sg.edu.nus.iss.wellness.ui.showError
import sg.edu.nus.iss.wellness.ui.wireBottomNav

/**
 * RAG chatbot screen: ask a wellness question and view chat history.
 *
 * @author Tiong Zhong Cheng, Tang Chee Seng, Abu Bakar Nasir
 */

class ChatActivity : AppCompatActivity() {
    private val scope = MainScope()
    private lateinit var binding: ActivityChatBinding
    private lateinit var tokenStore: TokenStore
    private lateinit var api: ApiService
    private lateinit var streamClient: ChatStreamClient
    private lateinit var adapter: ChatAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Source of truth for the visible conversation (oldest first). A live streaming answer
    // is appended here as a pending row (id == 0) and grows in place until persisted.
    private val messages = mutableListOf<ChatResponse>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tokenStore = TokenStore(this)
        if (tokenStore.token().isNullOrBlank()) {
            goToLogin()
            return
        }
        api = ApiClient.create(tokenStore)


        streamClient = ChatStreamClient(tokenStore)

            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(this, binding.rootContainer)

        // Keep the input above the keyboard in edge-to-edge mode.
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootContainer) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(binding.rootContainer)

        highlightTab(
            listOf(
                binding.bottomNav.dashboardButton,
                binding.bottomNav.chatButton,
                binding.bottomNav.recommendationsButton,
                binding.bottomNav.profileButton
            ),
            binding.bottomNav.chatButton
        )
        wireBottomNav(binding.bottomNav, ChatActivity::class.java)

        binding.sendButton.setOnClickListener {
            val text = binding.questionInput.text.toString().trim()
            if (text.isBlank()) {
                Toast.makeText(this, "Enter a wellness question before sending.", Toast.LENGTH_SHORT).show()
            } else {
                sendChat(text)
            }
        }
        binding.swipeRefresh.setOnRefreshListener { loadChatHistory() }

        adapter = ChatAdapter(this, emptyList())
        binding.chatListView.adapter = adapter

        loadChatHistory()
    }

    /**
     * Read the last known GPS fix, then invoke [onReady] with lat/lon (or null, null).
     * Null is a valid outcome — the agent falls back to the national average. If permission
     * is missing we request it for next time and proceed with null values first, so a Send is never
     * blocked while waiting on a dialog.
     */
    private fun readLocation(onReady: (Double?, Double?) -> Unit) {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            onReady(null, null)
            return
        }
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { loc -> onReady(loc?.latitude, loc?.longitude) }
                .addOnFailureListener { onReady(null, null) }
        } catch (_: SecurityException) {
            onReady(null, null)
        }
    }

    // Registered once for the Activity. The result is intentionally ignored: whether the
    // user grants or denies, we proceed with the send. Denied → null lat/lon → the agent
    // falls back to the national average. We only ask so a *future* send can read a fix.
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op: sendChat already read (or defaulted) the location for this turn */ }

    private fun sendChat(question: String) {
        binding.statusContainer.removeAllViews()
        addStateBlock(binding.statusContainer, "Thinking", "Local RAG and Ollama may take a little while.", "AI")
        binding.sendButton.isEnabled = false
        binding.questionInput.setText("")

        // Append the live answer row.
        val pendingIndex = messages.size
        messages.add(pendingMessage(question, "", emptyList()))
        renderMessages()

        val answer = StringBuilder()
        var sources: List<SourceSnippet> = emptyList()
        // Ignore late stream failures after terminal frames.
        var terminal = false

        // Read the GPS fix first (async); the stream call must run inside the callback,
        // since lat/lon only exist there. readLocation invokes onReady on the main thread,
        // so re-enter a coroutine before calling the suspend stream().
        readLocation { lat, lon ->
            scope.launch {
                runCatching {
                    streamClient.stream(question, lat, lon) { event ->
                        when (event) {
                            is ChatStreamEvent.Sources -> {
                                binding.statusContainer.removeAllViews()
                                sources = event.sources
                                updatePending(pendingIndex, question, answer.toString(), sources)
                            }
                            is ChatStreamEvent.Token -> {
                                binding.statusContainer.removeAllViews()
                                answer.append(event.text)
                                updatePending(pendingIndex, question, answer.toString(), sources)
                            }
                            is ChatStreamEvent.Done -> {
                                terminal = true
                                binding.sendButton.isEnabled = true
                                // Replace the pending row with the persisted server copy (correct
                                // id, timestamp, and stored sources) by reloading history.
                                loadChatHistory()
                            }
                            is ChatStreamEvent.Error -> {
                                terminal = true
                                binding.sendButton.isEnabled = true
                                dropPendingMessages()
                                showError(
                                    binding.statusContainer,
                                    event.message,
                                    "Keep your question and retry when services are running."
                                )
                            }
                        }
                    }
                }.onFailure {
                    if (terminal) return@onFailure
                    binding.sendButton.isEnabled = true
                    dropPendingMessages()
                    showError(
                        binding.statusContainer,
                        apiErrorMessage("Chatbot unavailable", it),
                        "Keep your question and retry when services are running."
                    )
                }
            }
        }
    }

    private fun pendingMessage(question: String, answer: String, sources: List<SourceSnippet>) =
        ChatResponse(id = 0, question = question, answer = answer, sources = sources, modelName = null, createdAt = null)

    private fun updatePending(index: Int, question: String, answer: String, sources: List<SourceSnippet>) {
        if (index >= messages.size) return
        messages[index] = pendingMessage(question, answer, sources)
        renderMessages()
    }

    /** Remove any unpersisted streaming rows (id == 0) after a failure. */
    private fun dropPendingMessages() {
        messages.removeAll { it.id == 0L }
        renderMessages()
    }

    private fun renderMessages() {
        adapter.submit(messages.toList())
        if (messages.isNotEmpty()) {
            binding.chatListView.setSelection(messages.size - 1)
        }
    }

    private fun loadChatHistory() {
        scope.launch {
            runCatching { api.chatHistory() }
                .onSuccess { history ->
                    binding.statusContainer.removeAllViews()
                    if (history.isEmpty()) {
                        addStateBlock(
                            binding.statusContainer,
                            "No chat yet",
                            "Ask a wellness habit question to start a RAG-backed conversation.",
                            "?"
                        )
                    }
                    // Backend returns newest-first; reverse so the latest exchange sits at the
                    // bottom next to the input box, then scroll to it.
                    messages.clear()
                    messages.addAll(history.reversed())
                    renderMessages()
                }
                .onFailure {
                    binding.statusContainer.removeAllViews()
                    showError(
                        binding.statusContainer,
                        "Could not load chat history",
                        "You can still retry after checking backend connectivity."
                    )
                }
            // Always stop the pull-to-refresh spinner, on both success and failure.
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
