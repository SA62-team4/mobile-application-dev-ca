package sg.edu.nus.iss.wellness

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import sg.edu.nus.iss.wellness.adapter.RecommendationAdapter
import sg.edu.nus.iss.wellness.api.ApiClient
import sg.edu.nus.iss.wellness.api.ApiService
import sg.edu.nus.iss.wellness.api.RecommendationResponse
import sg.edu.nus.iss.wellness.databinding.ActivityRecommendationsBinding
import sg.edu.nus.iss.wellness.ui.addStateBlock
import sg.edu.nus.iss.wellness.ui.apiErrorMessage
import sg.edu.nus.iss.wellness.ui.highlightTab
import sg.edu.nus.iss.wellness.ui.showError

/**
 * Displays AI-generated wellness recommendations and lets the user request a new one.
 *
 * @author SA62 Team
 */
class RecommendationsActivity : AppCompatActivity() {
    private val scope = MainScope()
    private lateinit var binding: ActivityRecommendationsBinding
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

        binding = ActivityRecommendationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(this, binding.rootContainer)

        highlightTab(
            listOf(
                binding.bottomNav.dashboardButton,
                binding.bottomNav.chatButton,
                binding.bottomNav.recommendationsButton,
                binding.bottomNav.profileButton
            ),
            binding.bottomNav.recommendationsButton
        )

        val headerView = layoutInflater.inflate(R.layout.header_generate_button, binding.recommendationsListView, false)
        headerView.findViewById<Button>(R.id.generateButton).setOnClickListener { generateRecommendation() }
        binding.recommendationsListView.addHeaderView(headerView)
        binding.recommendationsListView.emptyView = binding.emptyStateContainer

        loadRecommendations()
    }

    private fun loadRecommendations() {
        binding.emptyStateContainer.removeAllViews()
        addStateBlock(binding.emptyStateContainer, "Loading recommendations", "Fetching generated guidance from the backend.", "...")
        binding.recommendationsListView.adapter = RecommendationAdapter(this, emptyList())
        scope.launch {
            runCatching { api.recommendations() }
                .onSuccess { renderRecommendations(it) }
                .onFailure {
                    binding.emptyStateContainer.removeAllViews()
                    showError(binding.emptyStateContainer, "Could not load recommendations.", "Check backend, Python AI service, and Ollama status.")
                }
        }
    }

    private fun renderRecommendations(recommendations: List<RecommendationResponse>) {
        binding.emptyStateContainer.removeAllViews()
        if (recommendations.isEmpty()) {
            addStateBlock(binding.emptyStateContainer, "No recommendations yet", "Generate one after adding wellness records.", "+")
        }
        binding.recommendationsListView.adapter = RecommendationAdapter(this, recommendations)
    }

    private fun generateRecommendation() {
        binding.emptyStateContainer.removeAllViews()
        addStateBlock(binding.emptyStateContainer, "Generating recommendation", "Local AI may take up to a minute. Duplicate submissions are disabled.", "AI")
        binding.recommendationsListView.adapter = RecommendationAdapter(this, emptyList())
        scope.launch {
            runCatching { api.generateRecommendation() }
                .onSuccess { loadRecommendations() }
                .onFailure {
                    binding.emptyStateContainer.removeAllViews()
                    showError(
                        binding.emptyStateContainer,
                        apiErrorMessage("Could not generate recommendation", it),
                        "Do not pretend a recommendation was saved. Retry after services recover."
                    )
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
