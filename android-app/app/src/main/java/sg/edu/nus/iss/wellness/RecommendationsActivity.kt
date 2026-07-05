package sg.edu.nus.iss.wellness

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import sg.edu.nus.iss.wellness.api.ApiClient
import sg.edu.nus.iss.wellness.api.ApiService
import sg.edu.nus.iss.wellness.api.RecommendationResponse
import sg.edu.nus.iss.wellness.databinding.ActivityRecommendationsBinding
import sg.edu.nus.iss.wellness.ui.accent
import sg.edu.nus.iss.wellness.ui.addStateBlock
import sg.edu.nus.iss.wellness.ui.apiErrorMessage
import sg.edu.nus.iss.wellness.ui.body
import sg.edu.nus.iss.wellness.ui.caption
import sg.edu.nus.iss.wellness.ui.card
import sg.edu.nus.iss.wellness.ui.highlightTab
import sg.edu.nus.iss.wellness.ui.showError
import sg.edu.nus.iss.wellness.ui.title

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

        binding.generateButton.setOnClickListener { generateRecommendation() }

        loadRecommendations()
    }

    private fun loadRecommendations() {
        binding.recommendationsContainer.removeAllViews()
        addStateBlock(binding.recommendationsContainer, "Loading recommendations", "Fetching generated guidance from the backend.", "...")
        scope.launch {
            runCatching { api.recommendations() }
                .onSuccess { renderRecommendations(it) }
                .onFailure {
                    showError(binding.recommendationsContainer, "Could not load recommendations.", "Check backend, Python AI service, and Ollama status.")
                }
        }
    }

    private fun renderRecommendations(recommendations: List<RecommendationResponse>) {
        binding.recommendationsContainer.removeAllViews()
        if (recommendations.isEmpty()) {
            addStateBlock(binding.recommendationsContainer, "No recommendations yet", "Generate one after adding wellness records.", "+")
            return
        }
        recommendations.forEach { rec ->
            val recCard = card(fillColor = getColor(R.color.bg_subtle), stroke = getColor(R.color.bg_subtle))
            recCard.addView(title(rec.title, 16))
            recCard.addView(accent(rec.trendSummary))
            recCard.addView(body(rec.recommendationText))
            if (rec.actionItems.isNotEmpty()) {
                recCard.addView(caption("Actions"))
                rec.actionItems.forEach { recCard.addView(body("- $it")) }
            }
            rec.createdAt?.let { recCard.addView(caption("Generated $it")) }
            binding.recommendationsContainer.addView(recCard)
        }
    }

    private fun generateRecommendation() {
        binding.recommendationsContainer.removeAllViews()
        addStateBlock(binding.recommendationsContainer, "Generating recommendation", "Local AI may take up to a minute. Duplicate submissions are disabled.", "AI")
        scope.launch {
            runCatching { api.generateRecommendation() }
                .onSuccess { loadRecommendations() }
                .onFailure {
                    showError(
                        binding.recommendationsContainer,
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
