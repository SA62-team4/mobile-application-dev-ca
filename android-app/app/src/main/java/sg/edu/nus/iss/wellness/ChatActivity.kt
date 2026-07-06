package sg.edu.nus.iss.wellness

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
 * T-405 enhancements over the T-701 base: the input row is pinned to the bottom of the
 * screen (not the list header), the history list supports pull-to-refresh, the newest
 * exchange is anchored at the bottom next to the input, and source snippets render as
 * Material chips (see ChatAdapter). State messages continue to use the shared
 * addStateBlock/showError helpers.
 *
 * @author SA62 Team
 * @author Tang Chee Seng, with assistance from Claude (T-405 enhancements)
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

        // Lift the bottom input above the soft keyboard. With edge-to-edge the window draws
        // behind the IME, so adjustResize alone is not enough on Android 15 — pad the root by
        // the keyboard height (max with the nav-bar inset) whenever the IME shows. Pairs with
        // android:windowSoftInputMode="adjustResize" on this activity in the manifest.
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

        binding.chatListView.adapter = ChatAdapter(this, emptyList())

        loadChatHistory()
    }

    private fun sendChat(question: String) {
        binding.statusContainer.removeAllViews()
        addStateBlock(binding.statusContainer, "Thinking", "Local RAG and Ollama may take a little while.", "AI")
        binding.sendButton.isEnabled = false
        scope.launch {
            runCatching { api.sendChat(ChatRequest(question)) }
                .onSuccess {
                    binding.sendButton.isEnabled = true
                    binding.questionInput.setText("")
                    loadChatHistory()
                }
                .onFailure {
                    binding.sendButton.isEnabled = true
                    binding.statusContainer.removeAllViews()
                    showError(
                        binding.statusContainer,
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
                    binding.statusContainer.removeAllViews()
                    if (messages.isEmpty()) {
                        addStateBlock(
                            binding.statusContainer,
                            "No chat yet",
                            "Ask a wellness habit question to start a RAG-backed conversation.",
                            "?"
                        )
                    }
                    // Backend returns newest-first; reverse so the latest exchange sits at the
                    // bottom next to the input box, then scroll to it.
                    binding.chatListView.adapter = ChatAdapter(this@ChatActivity, messages.reversed())
                    if (messages.isNotEmpty()) {
                        binding.chatListView.setSelection(messages.size - 1)
                    }
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
