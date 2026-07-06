package sg.edu.nus.iss.wellness

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
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

        val headerView = layoutInflater.inflate(R.layout.header_chat_input, binding.chatListView, false)
        questionInput = headerView.findViewById(R.id.questionInput)
        headerView.findViewById<Button>(R.id.sendButton).setOnClickListener {
            val text = questionInput.text.toString().trim()
            if (text.isBlank()) {
                Toast.makeText(this, "Enter a wellness question before sending.", Toast.LENGTH_SHORT).show()
            } else {
                sendChat(text)
            }
        }
        binding.chatListView.addHeaderView(headerView)
        binding.chatListView.emptyView = binding.emptyStateContainer

        loadChatHistory()
    }

    private fun sendChat(question: String) {
        binding.emptyStateContainer.removeAllViews()
        addStateBlock(binding.emptyStateContainer, "Thinking", "Local RAG and Ollama may take a little while.", "AI")
        binding.chatListView.adapter = ChatAdapter(this, emptyList())
        scope.launch {
            runCatching { api.sendChat(ChatRequest(question)) }
                .onSuccess {
                    questionInput.setText("")
                    loadChatHistory()
                }
                .onFailure {
                    binding.emptyStateContainer.removeAllViews()
                    showError(
                        binding.emptyStateContainer,
                        apiErrorMessage("Chatbot unavailable", it),
                        "Keep your question and retry when services are running."
                    )
                }
        }
    }

    private fun loadChatHistory() {
        binding.emptyStateContainer.removeAllViews()
        scope.launch {
            runCatching { api.chatHistory() }
                .onSuccess { messages ->
                    if (messages.isEmpty()) {
                        addStateBlock(binding.emptyStateContainer, "No chat yet", "Ask a wellness habit question to start a RAG-backed conversation.", "?")
                    }
                    binding.chatListView.adapter = ChatAdapter(this@ChatActivity, messages)
                }
                .onFailure {
                    showError(binding.emptyStateContainer, "Could not load chat history", "You can still retry after checking backend connectivity.")
                    binding.chatListView.adapter = ChatAdapter(this@ChatActivity, emptyList())
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
