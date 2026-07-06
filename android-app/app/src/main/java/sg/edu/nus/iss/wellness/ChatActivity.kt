package sg.edu.nus.iss.wellness

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import sg.edu.nus.iss.wellness.adapter.ChatAdapter
import sg.edu.nus.iss.wellness.api.ApiClient
import sg.edu.nus.iss.wellness.api.ApiService
import sg.edu.nus.iss.wellness.api.ChatRequest
import sg.edu.nus.iss.wellness.databinding.ActivityChatBinding
import sg.edu.nus.iss.wellness.ui.addStateBlock
import sg.edu.nus.iss.wellness.ui.apiErrorMessage
import sg.edu.nus.iss.wellness.ui.highlightTab
import sg.edu.nus.iss.wellness.ui.showError
import sg.edu.nus.iss.wellness.ui.wireBottomNav

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
    private lateinit var questionInput: EditText
    private lateinit var sendButton: Button
    private lateinit var statusContainer: LinearLayout

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
        wireBottomNav(binding.bottomNav, ChatActivity::class.java)

        val headerView = layoutInflater.inflate(R.layout.header_chat_input, binding.chatListView, false)
        questionInput = headerView.findViewById(R.id.questionInput)
        sendButton = headerView.findViewById(R.id.sendButton)
        statusContainer = headerView.findViewById(R.id.statusContainer)
        sendButton.setOnClickListener {
            val text = questionInput.text.toString().trim()
            if (text.isBlank()) {
                Toast.makeText(this, "Enter a wellness question before sending.", Toast.LENGTH_SHORT).show()
            } else {
                sendChat(text)
            }
        }
        binding.chatListView.addHeaderView(headerView)
        binding.chatListView.adapter = ChatAdapter(this, emptyList())

        loadChatHistory()
    }

    private fun sendChat(question: String) {
        statusContainer.removeAllViews()
        addStateBlock(statusContainer, "Thinking", "Local RAG and Ollama may take a little while.", "AI")
        sendButton.isEnabled = false
        scope.launch {
            runCatching { api.sendChat(ChatRequest(question)) }
                .onSuccess {
                    sendButton.isEnabled = true
                    questionInput.setText("")
                    loadChatHistory()
                }
                .onFailure {
                    sendButton.isEnabled = true
                    statusContainer.removeAllViews()
                    showError(
                        statusContainer,
                        apiErrorMessage("Chatbot unavailable", it),
                        "Keep your question and retry when services are running."
                    )
                }
        }
    }

    private fun loadChatHistory() {
        scope.launch {
            runCatching { api.chatHistory() }
                .onSuccess { messages ->
                    statusContainer.removeAllViews()
                    if (messages.isEmpty()) {
                        addStateBlock(statusContainer, "No chat yet", "Ask a wellness habit question to start a RAG-backed conversation.", "?")
                    }
                    binding.chatListView.adapter = ChatAdapter(this@ChatActivity, messages)
                }
                .onFailure {
                    statusContainer.removeAllViews()
                    showError(statusContainer, "Could not load chat history", "You can still retry after checking backend connectivity.")
                }
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
