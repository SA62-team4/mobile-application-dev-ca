package sg.edu.nus.iss.wellness

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import sg.edu.nus.iss.wellness.api.ApiClient
import sg.edu.nus.iss.wellness.api.ApiService
import sg.edu.nus.iss.wellness.api.ChatRequest
import sg.edu.nus.iss.wellness.api.ChatResponse
import sg.edu.nus.iss.wellness.databinding.ActivityChatBinding
import sg.edu.nus.iss.wellness.ui.addStateBlock
import sg.edu.nus.iss.wellness.ui.apiErrorMessage
import sg.edu.nus.iss.wellness.ui.caption
import sg.edu.nus.iss.wellness.ui.chatBubble
import sg.edu.nus.iss.wellness.ui.highlightTab
import sg.edu.nus.iss.wellness.ui.showError

/**
 * RAG chatbot screen: ask a wellness question and view chat history.
 *
 * @author SA62 Team
 */
class ChatActivity : AppCompatActivity() {
    private val scope = MainScope()
    private lateinit var binding: ActivityChatBinding
    private lateinit var tokenStore: TokenStore
    private lateinit var api: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenStore = TokenStore(this)
        if (tokenStore.token().isNullOrBlank()) {
            goToLogin()
            return
        }
        api = ApiClient.create(tokenStore)

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(this, binding.rootContainer)

        highlightTab(
            listOf(
                binding.bottomNav.dashboardButton,
                binding.bottomNav.chatButton,
                binding.bottomNav.recommendationsButton,
                binding.bottomNav.profileButton
            ),
            binding.bottomNav.chatButton
        )

        binding.sendButton.setOnClickListener {
            val text = binding.questionInput.text.toString().trim()
            if (text.isBlank()) {
                addStateBlock(binding.chatMessagesContainer, "Question required", "Enter a wellness question before sending.", "!")
            } else {
                sendChat(text)
            }
        }

        loadChatHistory()
    }

    private fun sendChat(question: String) {
        binding.chatMessagesContainer.removeAllViews()
        addStateBlock(binding.chatMessagesContainer, "Thinking", "Local RAG and Ollama may take a little while.", "AI")
        scope.launch {
            runCatching { api.sendChat(ChatRequest(question)) }
                .onSuccess { loadChatHistory() }
                .onFailure {
                    showError(
                        binding.chatMessagesContainer,
                        apiErrorMessage("Chatbot unavailable", it),
                        "Keep your question and retry when services are running."
                    )
                }
        }
    }

    private fun loadChatHistory() {
        binding.chatMessagesContainer.removeAllViews()
        scope.launch {
            runCatching { api.chatHistory() }
                .onSuccess { messages ->
                    if (messages.isEmpty()) {
                        addStateBlock(binding.chatMessagesContainer, "No chat yet", "Ask a wellness habit question to start a RAG-backed conversation.", "?")
                    } else {
                        messages.forEach(::addChatPair)
                    }
                }
                .onFailure {
                    showError(binding.chatMessagesContainer, "Could not load chat history", "You can still retry after checking backend connectivity.")
                }
        }
    }

    private fun addChatPair(message: ChatResponse) {
        binding.chatMessagesContainer.addView(chatBubble("You", message.question, true))
        binding.chatMessagesContainer.addView(chatBubble("Assistant", message.answer, false))
        val sources = message.sources.orEmpty()
        if (sources.isNotEmpty()) {
            binding.chatMessagesContainer.addView(caption("Sources: ${sources.joinToString { it.title }}"))
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
