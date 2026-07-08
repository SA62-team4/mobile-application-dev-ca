package sg.edu.nus.iss.wellness

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    private var progressJob: Job? = null
    private var streamJob: Job? = null
    private var activeQuestion: String? = null
    private var allowUiUpdates = true

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
        binding.stopButton.setOnClickListener { stopChat() }
        binding.swipeRefresh.setOnRefreshListener { loadChatHistory() }

        adapter = ChatAdapter(this, emptyList())
        binding.chatListView.adapter = adapter

        loadChatHistory()
    }

    /** Mutable state carried across the streaming callbacks for one question. */
    private class StreamState {
        val answer = StringBuilder()
        var sources: List<SourceSnippet> = emptyList()
        // Ignore late stream failures after terminal frames.
        var terminal = false
    }

    private fun sendChat(question: String) {
        allowUiUpdates = true
        binding.statusContainer.removeAllViews()
        activeQuestion = question
        showStopButton()
        binding.questionInput.setText("")

        // Append the live answer row.
        val pendingIndex = messages.size
        messages.add(pendingMessage(question, getString(R.string.chat_progress_initial), emptyList()))
        renderMessages()
        showChatProgress(pendingIndex, question)

        val state = StreamState()
        streamJob = scope.launch {
            runCatching {
                streamClient.stream(question) { event ->
                    handleStreamEvent(state, event, pendingIndex, question)
                }
            }.onFailure { onStreamFailure(state, it) }
        }
    }

    private fun handleStreamEvent(state: StreamState, event: ChatStreamEvent, pendingIndex: Int, question: String) {
        if (!allowUiUpdates) {
            if (event is ChatStreamEvent.Done || event is ChatStreamEvent.Error) state.terminal = true
            return
        }
        when (event) {
            is ChatStreamEvent.Sources -> {
                state.sources = event.sources
                updatePending(pendingIndex, question, getString(R.string.chat_progress_rag), state.sources)
            }
            is ChatStreamEvent.Token -> {
                hideChatProgress()
                state.answer.append(event.text)
                updatePending(pendingIndex, question, state.answer.toString(), state.sources)
            }
            is ChatStreamEvent.Done -> {
                state.terminal = true
                finishStreaming()
                // Replace the pending row with the persisted server copy (correct
                // id, timestamp, and stored sources) by reloading history.
                loadChatHistory()
            }
            is ChatStreamEvent.Error -> {
                state.terminal = true
                finishStreaming()
                dropPendingMessages()
                showError(
                    binding.statusContainer,
                    event.message,
                    "Keep your question and retry when services are running."
                )
            }
        }
    }

    private fun onStreamFailure(state: StreamState, throwable: Throwable) {
        if (throwable is CancellationException) return
        if (state.terminal) return
        if (!allowUiUpdates) return
        finishStreaming()
        dropPendingMessages()
        showError(
            binding.statusContainer,
            apiErrorMessage("Chatbot unavailable", throwable),
            "Keep your question and retry when services are running."
        )
    }

    private fun stopChat() {
        val questionToRestore = activeQuestion.orEmpty()
        streamJob?.cancel()
        finishStreaming()
        dropPendingMessages()
        binding.questionInput.setText(questionToRestore)
        binding.questionInput.setSelection(binding.questionInput.text.length)
    }

    private fun finishStreaming() {
        hideChatProgress()
        streamJob = null
        activeQuestion = null
        showSendButton()
    }

    private fun showStopButton() {
        binding.sendButton.visibility = View.GONE
        binding.stopButton.visibility = View.VISIBLE
        binding.stopButton.isEnabled = true
    }

    private fun showSendButton() {
        binding.stopButton.visibility = View.GONE
        binding.sendButton.visibility = View.VISIBLE
        binding.sendButton.isEnabled = true
    }

    private fun showChatProgress(pendingIndex: Int, question: String) {
        val phases = listOf(
            getString(R.string.chat_progress_initial),
            getString(R.string.chat_progress_rag),
            getString(R.string.chat_progress_stream)
        )
        progressJob?.cancel()
        progressJob = scope.launch {
            var index = 0
            var dotCount = 0
            while (isActive) {
                delay(450)
                dotCount = (dotCount + 1) % 4
                if (dotCount == 0) {
                    index = (index + 1) % phases.size
                }
                val text = phases[index].trimEnd('.') + ".".repeat(dotCount.coerceAtLeast(1))
                updatePending(pendingIndex, question, text, emptyList())
            }
        }
    }

    private fun hideChatProgress() {
        progressJob?.cancel()
        progressJob = null
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
        allowUiUpdates = false
        progressJob?.cancel()
        if (isFinishing) {
            streamJob?.cancel()
            scope.cancel()
        }
    }
}
